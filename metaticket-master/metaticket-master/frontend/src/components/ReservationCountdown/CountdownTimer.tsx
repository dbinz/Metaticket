import { useEffect, useState } from 'react'

const formatTime = (time: number): string => {
  const minutes = Math.floor(time / 60000)
  const seconds = Math.floor((time % 60000) / 1000)
  return `${minutes.toString().padStart(2, '0')}:${seconds
    .toString()
    .padStart(2, '0')}`
}

const CountdownTimer = ({
  startTime,
  duration,
  onTimeout,
}: {
  startTime: number
  duration: number
  onTimeout: () => void
}) => {
  const [timeRemaining, setTimeRemaining] = useState<number>(duration)

  useEffect(() => {
    const currentTime = new Date().getTime()
    const initialTimeRemaining = duration - (currentTime - startTime)

    if (initialTimeRemaining > 0) {
      setTimeRemaining(initialTimeRemaining)
    } else {
      setTimeRemaining(0)
    }

    const timer = setInterval(() => {
      const updatedTimeRemaining = duration - (new Date().getTime() - startTime)
      if (updatedTimeRemaining > 0) {
        setTimeRemaining(updatedTimeRemaining)
      } else {
        setTimeRemaining(0)
        clearInterval(timer)
        onTimeout()
      }
    }, 1000)

    return () => clearInterval(timer)
  }, [startTime])

  return <div className="text-xl">{formatTime(timeRemaining)}</div>
}

export { CountdownTimer }
