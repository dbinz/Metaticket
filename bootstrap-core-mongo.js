const localDb = db.getSiblingDB('metaticket');
// A startup-only default with no usable gateway accounts. Real payments remain unconfigured.
if (!localDb.getCollection('pg-account-config').findOne()) {
  localDb.getCollection('pg-account-config').insertOne({
    _id: ObjectId('000000000000000000000001'),
    pgAccountDefaultCode: 'LOCAL_UNCONFIGURED',
    pgAccount: {},
  });
}
print('Local payment bootstrap checked; no real gateway credentials configured.');
const eventCategories = [
  ['music', '\u00c2m nh\u1ea1c'],
  ['workshop', 'Workshop'],
  ['cinema', '\u0110i\u1ec7n \u1ea3nh'],
  ['stage', 'S\u00e2n kh\u1ea5u'],
  ['conference', 'H\u1ed9i th\u1ea3o'],
  ['sport', 'Th\u1ec3 thao'],
  ['children', 'Thi\u1ebfu nhi'],
  ['tourist', 'Du l\u1ecbch'],
];
for (const [id, name] of eventCategories) {
  localDb.getCollection('event-category').updateOne({_id:id}, {$setOnInsert:{name}}, {upsert:true});
}
print('Checked 8 event categories for the local event filters.');
for (const city of [
  {code:'01', name:'H\u00e0 N\u1ed9i'},
  {code:'79', name:'H\u1ed3 Ch\u00ed Minh'},
]) {
  localDb.getCollection('city').updateOne({code:city.code}, {$setOnInsert:city}, {upsert:true});
}
print('Checked Hanoi and Ho Chi Minh City for location filters.');
