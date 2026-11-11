import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClientService } from '../http-client/http-client.service';
import { apiUrl } from '../../constants';
import { VoteMeResponse, VoteOptionsResponse, VoteRequest, VoteSubmitResponse } from '../../entities';

@Injectable({
  providedIn: 'root',
})
export class VoteService {
  constructor(private _http: HttpClientService) {}

  getOptions(): Observable<VoteOptionsResponse> {
    return this._http.getJson(apiUrl.getVoteOptions);
  }

  getMyVote(): Observable<VoteMeResponse> {
    return this._http.getJson(apiUrl.getMyVote);
  }

  submitVote(req: VoteRequest): Observable<VoteSubmitResponse> {
    return this._http.postJson(apiUrl.submitVote, req);
  }
}
