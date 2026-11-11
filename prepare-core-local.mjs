import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const root = import.meta.dirname;
const source = path.join(root, 'metaticket-master/metaticket-master/kotlin');
const target = path.join(root, '.local/core');
fs.mkdirSync(target, {recursive:true});
const secretFile = path.join(target, 'secrets.json');
let secrets;
if (fs.existsSync(secretFile)) secrets = JSON.parse(fs.readFileSync(secretFile, 'utf8'));
else {
  const pair = crypto.generateKeyPairSync('rsa', {modulusLength:2048});
  secrets = {
    aes: crypto.randomBytes(8).toString('hex'),
    jwt: crypto.randomBytes(32).toString('hex'),
    qr: crypto.randomBytes(8).toString('hex'),
    privateKey: pair.privateKey.export({type:'pkcs8',format:'der'}).toString('base64'),
    publicKey: pair.publicKey.export({type:'spki',format:'der'}).toString('base64'),
  };
  fs.writeFileSync(secretFile, JSON.stringify(secrets, null, 2));
}
const services = fs.readdirSync(source).filter(n => n.startsWith('service.') && n !== 'service.news');
for (const service of services) {
  const sample = path.join(source, service, 'conf-sample');
  if (!fs.existsSync(sample)) continue;
  const dest = path.join(target, service);
  fs.cpSync(sample, dest, {recursive:true});
  const file = path.join(dest, 'application.conf');
  let config = fs.readFileSync(file, 'utf8');
  config = config.replaceAll('CHANGE_ME_16BYTE', secrets.aes)
    .replaceAll('mongodb://localhost:47017', 'mongodb://localhost:47017/?replicaSet=localrs')
    .replaceAll('CHANGE_ME_QR_16B', secrets.qr)
    .replaceAll('10.0.1.12:43790', '127.0.0.1:43790')
    .replaceAll('10.0.1.12:43791', '127.0.0.1:43791')
    .replaceAll('10.0.1.12:43792', '127.0.0.1:43792')
    .replaceAll('10.0.1.12:49092,10.0.1.12:49093,10.0.1.12:49094', '127.0.0.1:49092')
    .replaceAll('http://10.0.1.12:48080', 'http://search:48080')
    .replaceAll('https://admin.staging.metaticket.vn', 'http://localhost:4200')
    .replaceAll('https://staging.metaticket.vn', 'http://localhost:4200')
    .replace(/xApiKey:\s*"CHANGE_ME"/g, 'xApiKey: "local-development-only"')
    .replace(/privateKey:\s*"CHANGE_ME"/g, `privateKey: "${secrets.privateKey}"`)
    .replace(/publicKey:\s*"CHANGE_ME"/g, `publicKey: "${secrets.publicKey}"`)
    .replace(/salt\s*[:=]\s*"CHANGE_ME"/g, 'salt = "$2a$10$N9qo8uLOickgx2ZMRZoMye"')
    .replace(/secret\s*[:=]\s*"CHANGE_ME"/g, `secret = "${secrets.jwt}"`);
  if (service === 'service.order.datacollector') config = config.replaceAll('3032','4032').replaceAll('3132','4132').replaceAll('3104','4104');
  // Confine outbound integrations to local endpoints until real credentials are configured.
  config += '\ncloudflare.baseUrl = "http://127.0.0.1:1/"\nzns.sendMessageUrl = "http://127.0.0.1:1/"\n';
  fs.writeFileSync(file, config);
  fs.writeFileSync(path.join(dest,'logback.xml'), '<configuration><appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender"><encoder><pattern>%date %-5level %logger{24} %msg%n</pattern></encoder></appender><root level="INFO"><appender-ref ref="CONSOLE"/></root></configuration>');
  if (service === 'service.notification') fs.writeFileSync(path.join(dest,'simplejavamail.properties'), 'simplejavamail.smtp.host=127.0.0.1\nsimplejavamail.smtp.port=1025\nsimplejavamail.transportstrategy=SMTP\n');
}
const ports = [8081,4011,4012,4003,4004,4005,4006,4007,4008,4009,4010,4020,4030,4031,4032,4040,4050,4051];
const compose = {
  name:'metaticket-local',
  services:{
    'core-net': {image:'alpine:3.21',command:['sleep','infinity'],ports:ports.map(p=>`127.0.0.1:${p}:${p}`)},
    mongo:{image:'mongo:7.0',network_mode:'service:core-net',command:['mongod','--port','47017','--bind_ip','127.0.0.1','--replSet','localrs'],volumes:['core-mongo:/data/db'],healthcheck:{test:['CMD','mongosh','--port','47017','--quiet','--eval','db.adminCommand({ping:1}).ok'],interval:'5s',timeout:'5s',retries:30}},
    kafka:{image:'apache/kafka:3.9.1',network_mode:'service:core-net',environment:{KAFKA_NODE_ID:'1',KAFKA_PROCESS_ROLES:'broker,controller',KAFKA_LISTENERS:'PLAINTEXT://127.0.0.1:49092,CONTROLLER://127.0.0.1:49093',KAFKA_ADVERTISED_LISTENERS:'PLAINTEXT://127.0.0.1:49092',KAFKA_CONTROLLER_LISTENER_NAMES:'CONTROLLER',KAFKA_LISTENER_SECURITY_PROTOCOL_MAP:'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT',KAFKA_CONTROLLER_QUORUM_VOTERS:'1@127.0.0.1:49093',KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR:'1',KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR:'1',KAFKA_TRANSACTION_STATE_LOG_MIN_ISR:'1',KAFKA_HEAP_OPTS:'-Xms128m -Xmx256m'},volumes:['core-kafka:/tmp/kraft-combined-logs']},
  },volumes:{'core-mongo':{},'core-kafka':{}},
};
compose.services['mongo-init']={image:'mongo:7.0',network_mode:'service:core-net',depends_on:{mongo:{condition:'service_healthy'}},entrypoint:['mongosh','--port','47017','--quiet','--eval','try { rs.status() } catch(e) { rs.initiate({_id:"localrs",members:[{_id:0,host:"127.0.0.1:47017"}]}) }; let ready=false; for(let i=0;i<60;i++){ if(db.hello().isWritablePrimary){ready=true;break}; sleep(1000) }; if(!ready) quit(1);']};
for(const p of [43790,43791,43792]) {
  compose.services[`redis-${p}`] = {image:'redis:7.4-alpine',network_mode:'service:core-net',command:['redis-server','--port',`${p}`,'--bind','127.0.0.1','--cluster-enabled','yes','--cluster-config-file','nodes.conf','--cluster-announce-ip','127.0.0.1','--cluster-announce-port',`${p}`,'--cluster-announce-bus-port',`${p+10000}`,'--appendonly','yes'],volumes:[`core-redis-${p}:/data`],healthcheck:{test:['CMD','redis-cli','-p',`${p}`,'ping'],interval:'5s',timeout:'3s',retries:30}};
  compose.volumes[`core-redis-${p}`]={};
}
compose.services['redis-init']={image:'redis:7.4-alpine',network_mode:'service:core-net',depends_on:Object.fromEntries([43790,43791,43792].map(p=>[`redis-${p}`,{condition:'service_healthy'}])),entrypoint:['sh','-c','if redis-cli -p 43790 cluster info | grep -q "cluster_state:ok"; then exit 0; fi; redis-cli --cluster create 127.0.0.1:43790 127.0.0.1:43791 127.0.0.1:43792 --cluster-replicas 0 --cluster-yes']};
for(const service of services) {
  compose.services[service]={image:'metaticket-core-local',...(service==='service.show'?{build:{context:'./metaticket-master/metaticket-master/kotlin',dockerfile:'Dockerfile.local'}}:{}),network_mode:'service:core-net',environment:{SERVICE:service,JAVA_TOOL_OPTIONS:'-Xms32m -Xmx192m -XX:ActiveProcessorCount=2 -Duser.timezone=Asia/Ho_Chi_Minh -Dlogback.configurationFile=conf/logback.xml'},volumes:[`./.local/core/${service}:/app/${service}/conf:ro`],depends_on:{'mongo-init':{condition:'service_completed_successfully'},'redis-init':{condition:'service_completed_successfully'},kafka:{condition:'service_started'}},mem_limit:'448m'};
}
compose.services['service.notification'].environment.JAVA_TOOL_OPTIONS += ' -Dsimplejavamail.smtp.host=127.0.0.1 -Dsimplejavamail.smtp.port=1025';
compose.services['service.notification'].entrypoint=['/bin/sh','-c','cd /app/service.notification && exec java -cp "conf:lib/*" metaticket.notification.Launcher -config=conf/application.conf'];
const routePorts = {'editor/seatplanner':4012,'editor/policydesc':4011,show:4003,ticket:4004,order:4005,payment:4006,jobrunr:4007,notification:4008,'ticket-slots':4009,qrcode:4010,accessory:4020,auth:4030,authorization:4031,odc:4032,admin:4040,'customer-user':4050,'customer-auth':4051};
const authLines = 'auth_request /_customer_auth; auth_request_set $customer_id $upstream_http_x_customer_user_id; auth_request_set $customer_phone $upstream_http_x_customer_phone; proxy_set_header X-Customer-User-Id $customer_id; proxy_set_header X-Customer-Phone $customer_phone;';
let nginx = 'events {}\nhttp { server { listen 8081; client_max_body_size 20m; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n';
nginx += 'location = /maintenance/status { default_type application/json; return 200 \'{"maintenance":false}\'; }\n';
nginx += 'location = /_customer_auth { internal; proxy_pass http://127.0.0.1:4051/internal/customer-auth/authenticate; proxy_pass_request_body off; proxy_set_header Content-Length ""; proxy_set_header Authorization $http_authorization; proxy_set_header Cookie $http_cookie; }\n';
nginx += 'location /api/public/metasearch/ { proxy_pass http://search:48080/public/metasearch/; }\n';
for (const [route, port] of Object.entries(routePorts)) {
  nginx += `location /api/public/${route}/ { proxy_pass http://127.0.0.1:${port}/public/${route}/; }\n`;
  const protectedRoute = ['customer-user','customer-auth','accessory','ticket-slots'].includes(route);
  nginx += `location /api/${route}/ { ${protectedRoute ? authLines : ''} proxy_pass http://127.0.0.1:${port}/${route}/; }\n`;
}
nginx += 'location / { return 404; } } }\n';
fs.writeFileSync(path.join(target,'nginx.conf'),nginx);
compose.services.gateway={image:'nginx:1.28-alpine',network_mode:'service:core-net',volumes:['./.local/core/nginx.conf:/etc/nginx/nginx.conf:ro'],depends_on:{'service.show':{condition:'service_started'},'service.customer.auth':{condition:'service_started'},search:{condition:'service_started'}}};
fs.writeFileSync(path.join(root,'compose.core.local.json'),JSON.stringify(compose,null,2));
console.log(`Prepared local configuration for ${services.length} core services.`);
