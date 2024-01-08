package audioinputstreams

import scala.collection.mutable.Queue
import scala.collection.mutable.Set
import dao.AudioFileHandler
import java.io._
import javax.sound.sampled._

import org.tritonus.share.sampled.FloatSampleBuffer

/**
 * javax.sound.sampled.AudioInputStream を継承した自作クラス
 * 複数の audio stream (ここでは AudioFileHandler を利用) を配置して混ぜ合わせる
 * @param audioFormat
 * @param arrangedAudioInputStreams Frame 単位で AudioFileHandler の開始位置を設定
 * @param attenuationPerStream
 */
class ArrangedMixingAudioInputStream(audioFormat: AudioFormat, arrangedAudioInputStreams: Seq[(Long, AudioFileHandler)],
                                     // Attenuate the stream by how many dB per mixed stream
                                     private val attenuationPerStream: Float = 0.1f)
  extends AudioInputStream(new ByteArrayInputStream(new Array[Byte](0)), audioFormat, AudioSystem.NOT_SPECIFIED) {

  private var framePos2: Long = 0L

  // audio input stream now being used
  private val audioInputStreamSet: Set[AudioInputStream] = Set()

  // audiostreams that have not been used yet
  private val audioStreamQueue: Queue[(Long, AudioFileHandler)] = Queue(arrangedAudioInputStreams: _*).sortBy(_._1)

  // set up the static mix buffer with initially no samples.
  private val mixBuffer = new FloatSampleBuffer(audioFormat.getChannels, 0, audioFormat.getSampleRate)

  // used for reading samples from the underlying streams.
  private val readBuffer = new FloatSampleBuffer

  // The linear factor to apply to all samples
  private val attenuationFactor = ArrangedMixingAudioInputStream.decibel2linear(-1.0f * attenuationPerStream)

  private var tempBuffer: Array[Byte] = null

  /**
    * The maximum of the frame length of the input stream is calculated and returned.
    * If at least one of the input streams has length <code>AudioInputStream.NOT_SPECIFIED</code>, this value is returned.
    */
  override def getFrameLength: Long = {
    arrangedAudioInputStreams.foldLeft(0L, Map(): Map[String, Long]) { (maxFrameLengthAndCache, framePos2Andhandler) =>
      val maxFrameLength = maxFrameLengthAndCache._1
      val lengthCache = maxFrameLengthAndCache._2
      val audioFramePos = framePos2Andhandler._1
      val handler = framePos2Andhandler._2
      // 各audio の最大
      val lLength = lengthCache.getOrElse(handler.audioFilePath, handler.getFrameLength)
      val newCache = lengthCache + (handler.audioFilePath -> lLength)
      if (lLength == AudioSystem.NOT_SPECIFIED || maxFrameLength == AudioSystem.NOT_SPECIFIED)
        (AudioSystem.NOT_SPECIFIED, newCache)
      else
        (Math.max(maxFrameLength, lLength + audioFramePos), newCache)
    }._1
  }

  @throws[IOException]
  override def read: Int = {
    val samples = new Array[Byte](1)
    val ret = read(samples)
    if (ret != 1) return -1
    samples(0)
  }

  /**
    * reads nLength samples and store them to abData
    */
  @throws[IOException]
  override def read(abData: Array[Byte], nOffset: Int, nLength: Int): Int = {

    val nFrameLength = nLength / getFormat.getFrameSize

    // set up the mix buffer with the requested size
    mixBuffer.changeSampleCount(nFrameLength, false)
    // initialize the mixBuffer with silence
    mixBuffer.makeSilence()

    if (audioStreamQueue.nonEmpty) {
      val (headFramePos, headAudioInputStream) = audioStreamQueue.head
      val nextFrame = nLength / getFormat.getFrameSize
      if (headFramePos < framePos2) {
        println("Invalid frame position!")
        throw new IOException
      } else if (headFramePos == framePos2) {
        // 頭にある場合はそれを audioInputStreamSet に入れる
        audioStreamQueue.dequeue()
        audioInputStreamSet.add(headAudioInputStream.getAudioInputStream)
        return read(abData, nOffset, nLength)
      } else if (headFramePos < framePos2 + nextFrame) {
        // 次の framePos2 の start 地点で分割
        val nowFramePos = framePos2
        val firstByteLen = ((headFramePos - nowFramePos) * getFormat.getFrameSize).toInt
        val secondNLength = nLength - firstByteLen
        val a = read(abData, nOffset, firstByteLen)
        val b = read(abData, nOffset + firstByteLen, secondNLength)
        return if (b == -1) -1 else a + b
      }
    }
    // the calculation below is not scala-like so later would like to be refactored
    // remember the maximum number of samples actually mixed
    var maxMixed = 0
    var sampleCount = 0
    audioInputStreamSet.foreach(stream => {
      val needRead = mixBuffer.getSampleCount * stream.getFormat.getFrameSize
      if (tempBuffer == null || tempBuffer.length < needRead) tempBuffer = Array.fill[Byte](needRead)(0)
      val bytesRead = stream.read(tempBuffer, 0, needRead)
      if (bytesRead == -1) {
        audioInputStreamSet.remove(stream)
        stream.close()
      } else {
        // now convert this buffer to float samples
        readBuffer.initFromByteArray(tempBuffer, 0, bytesRead, stream.getFormat)
        sampleCount = readBuffer.getSampleCount
        maxMixed = Math.max(sampleCount, maxMixed)
        val maxChannels = Math.min(mixBuffer.getChannelCount, readBuffer.getChannelCount)
        (0 until maxChannels).foreach { channel =>
          // get the arrays of the normalized float samples
          val readSamples = readBuffer.getChannel(channel)
          val mixSamples = mixBuffer.getChannel(channel)
          val maxSamples = Math.min(mixBuffer.getSampleCount, readBuffer.getSampleCount)
          (0 until maxSamples).foreach { sample =>
            mixSamples(sample) += attenuationFactor * readSamples(sample)
          }
        }
      }
    })
    if (maxMixed == 0) {
      if (audioInputStreamSet.isEmpty && audioStreamQueue.isEmpty) {
        // nothing mixed, no more streams available: end of stream
        return -1
      }
      // 空白を挿入
      mixBuffer.convertToByteArray(0, mixBuffer.getSampleCount, abData, nOffset, getFormat)
      framePos2 += nLength / getFormat.getFrameSize
      return nLength
    }
    mixBuffer.convertToByteArray(0, maxMixed, abData, nOffset, getFormat)
    framePos2 += maxMixed
    maxMixed * getFormat.getFrameSize
  }

  /**
    * skips lLengnth from the framePos2
    */
  @throws[IOException]
  override def skip(lLength: Long): Long = {
    val skippedMax = audioInputStreamSet.map(_.skip(lLength)).max
    val nextFrame = lLength / getFormat.getFrameSize
    val headFramePosOverLlength = audioStreamQueue.filter(x => x._1 < framePos2 + nextFrame)
    val skippedMaxInQueue = headFramePosOverLlength.map(x => {
      audioStreamQueue.dequeue()
      val (headFramePos, headAudioInputStream) = x
      val audioInputStream = headAudioInputStream.getAudioInputStream
      audioInputStreamSet.add(audioInputStream)
      val skipped = audioInputStream.skip(lLength - (headFramePos - framePos2))
      skipped + (headFramePos - framePos2)
    }).max
    framePos2 += lLength / getFormat.getFrameSize
    Math.max(skippedMax, skippedMaxInQueue)
  }

  /**
    * The minimum of available() of all input stream is calculated and returned.
    */
  @throws[IOException]
  override def available: Int = {
    audioStreamQueue.map(_._2.available()).min
  }

  @throws[IOException]
  override def close() {
    audioInputStreamSet.foreach(_.close())
    audioInputStreamSet.foreach(audioInputStreamSet.remove(_))
  }

  /**
    * 実装出来ていない
    */
  override def mark(nReadLimit: Int) {
    // audioInputStreamSet.foreach(_.mark(nReadLimit))
  }

  /**
    * 実装できていない
    */
  @throws[IOException]
  override def reset() {
    // audioInputStreamSet.foreach(_.reset())
  }

  /**
    * 実装出来ていないので false を返す
    * 将来的に実装するかは未定
    */
  override def markSupported: Boolean = {
    // audioInputStreamSet.map(_.markSupported()).reduce(_ && _)
    false
  }

}

object ArrangedMixingAudioInputStream {
  def decibel2linear(decibels: Float): Float = Math.pow(10.0, decibels / 20.0).toFloat
}
