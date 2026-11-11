'use client'

import { StepDataProvider, useStepDataContext } from '@/components/BookingSteps/StepDataContext'
import { StepNavProvider } from '@/components/BookingSteps/StepNavContext'
import { StepsContainer } from '@/components/BookingSteps/StepsContainer'
import { ShowContextProvider } from '@/components/ShowContext'
import { PageLoader } from '@/components/utils/PageLoader'
import {ShowDocument, ShowSessionStatus, ShowSessionDocument} from '@/data/show'
import { useShowQuery } from '@/data/show/query'
import { StoreManager } from '@/data/storeManager'
import { useSearchParams } from 'next/navigation'
import { useState } from 'react'

export default function Home() {
  // set showId from Next.js page's query params
  const searchParams = useSearchParams()
  const showId = searchParams.get('showId') ?? ''
  const sessionId = searchParams.get('sessionId') ?? ''


  StoreManager.setScope(sessionId)

  const { data, isLoading, isError } = useShowQuery(showId)
  const [notHaveShow, setNotHaveShow] = useState(false)
  const [notHaveSession, setNotHaveSession] = useState(false)

  const getShow = (show: ShowDocument) => {
    if(show == null) {
      setNotHaveShow(true);
    }
    return show;
  }

  const getSessionFromShow = (sessions: ShowSessionDocument[]) => {
    var session = null
    for(var i = 0; i < sessions.length; i ++) {
      if(sessions[i].id == sessionId) {
        session = sessions[i];
        break;
      }
    }
    if(session == null || session.status != ShowSessionStatus.Open) {
      setNotHaveSession(true);
      session = sessions[0];
    }
    return session;
  }

  // TODO: handle error and loading state
  if (isError) {
    return <div>Something went wrong</div>
  }

  if (notHaveShow) {
    return <div>Show is not available</div>
  }

  if (notHaveSession) {
    return <div>Session is not available</div>
  }

  if (isLoading) {
    return <PageLoader />
  }

  return (
    <main className="p-3 sm:p-5">
      <div className="w-full pb-3 sm:pb-5">
        <img
          src={data.logoShowUrl || '/assets/frontend/icons/metaticket_logo.png'}
          alt={data.showName || 'Show banner'}
          className="m-auto max-w-[50%] max-h-[50px]"
        />
      </div>

      <ShowContextProvider
        value={{
          show: getShow(data),
          session: getSessionFromShow(data.sessions),
        }}
      >
        <StepNavProvider>
          <StepDataProvider>
            <StepsContainer />
          </StepDataProvider>
        </StepNavProvider>
      </ShowContextProvider>
    </main>
  )
}
