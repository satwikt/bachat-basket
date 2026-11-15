import { useRef, useState } from 'react'

/**
 * Microphone recorder shared by the merchant and customer pages.
 * start() resolves to an error message, or '' when recording has started.
 * When recording stops, `blob` holds the audio (webm, ogg or mp4 depending on the browser).
 */
export function useRecorder({ maxSeconds = 25 } = {}) {
  const [recording, setRecording] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const [blob, setBlob] = useState(null)
  const recorderRef = useRef(null)
  const timerRef = useRef(null)
  const chunksRef = useRef([])

  async function start() {
    let stream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch (e) {
      return 'Microphone blocked: ' + e.message
    }
    chunksRef.current = []
    setBlob(null)
    const rec = new MediaRecorder(stream)
    recorderRef.current = rec
    rec.ondataavailable = (e) => { if (e.data.size) chunksRef.current.push(e.data) }
    rec.onstop = () => {
      clearInterval(timerRef.current)
      stream.getTracks().forEach((t) => t.stop())
      setBlob(new Blob(chunksRef.current, { type: rec.mimeType || 'audio/webm' }))
      setRecording(false)
    }
    rec.start()
    setRecording(true)
    setSeconds(0)
    const t0 = Date.now()
    timerRef.current = setInterval(() => {
      const s = Math.floor((Date.now() - t0) / 1000)
      setSeconds(s)
      if (s >= maxSeconds) rec.stop()
    }, 250)
    return ''
  }

  function stop() {
    recorderRef.current?.stop()
  }

  return { recording, seconds, blob, start, stop }
}
