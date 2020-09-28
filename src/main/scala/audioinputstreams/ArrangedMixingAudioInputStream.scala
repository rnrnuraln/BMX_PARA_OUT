package audioinputstreams

import scala.collection.mutable.Queue
import scala.collection.mutable.Set

/*
 *	ArrangedMixingAudioInputStream
 */

import java.io._
import javax.sound.sampled._

import org.tritonus.share.sampled.FloatSampleBuffer

class ArrangedMixingAudioInputStream(audioFormat: AudioFormat, arrangedAudioInputStreams: Seq[(Long, Seq[AudioInputStream])])
  extends AudioInputStream(new ByteArrayInputStream(new Array[Byte](0)), audioFormat, AudioSystem.NOT_SPECIFIED) {

  // audio input stream now being used
  private val audioInputStreamSet: Set[AudioInputStream] = Set()

  // audiostreams that have not been used yet
  private val audioStreamQueue: Queue[(Long, Seq[AudioInputStream])] = Queue(arrangedAudioInputStreams: _*).sortBy(_._1)

  // set up the static mix buffer with initially no samples.
  private val mixBuffer = new FloatSampleBuffer(audioFormat.getChannels, 0, audioFormat.getSampleRate)

  // used for reading samples from the underlying streams.
  private val readBuffer = new FloatSampleBuffer

  // Attenuate the stream by how many dB per mixed stream.
  private val attenuationPerStream: Float = 0.1f

  // The linear factor to apply to all samples
  private val attenuationFactor = ArrangedMixingAudioInputStream.decibel2linear(-1.0f * attenuationPerStream * arrangedAudioInputStreams.map(_._2.length).max)

  private var tempBuffer: Array[Byte] = null

  /**
    * The maximum of the frame length of the input stream is calculated and returned.
    * If at least one of the input streams has length <code>AudioInputStream.NOT_SPECIFIED</code>, this value is returned.
    */
  override def getFrameLength: Long = {
    arrangedAudioInputStreams.foldLeft(0L) { (maxFrameLength, streams) =>
      val inStreamMax = streams._2.foldLeft(0L) { (max, stream) =>
        // それぞれの位置ごとの最大
        val lLength = stream.getFrameLength
        if (lLength == AudioSystem.NOT_SPECIFIED || max == AudioSystem.NOT_SPECIFIED)
          AudioSystem.NOT_SPECIFIED
        else
          Math.max(max, lLength) + streams._1
      }
      // 全体の最大と比較
      if (inStreamMax == AudioSystem.NOT_SPECIFIED || maxFrameLength == AudioSystem.NOT_SPECIFIED)
        AudioSystem.NOT_SPECIFIED
      else
        Math.max(maxFrameLength, inStreamMax)
    }
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
    // set up the mix buffer with the requested size
    mixBuffer.changeSampleCount(nLength / getFormat.getFrameSize, false)
    // initialize the mixBuffer with silence
    mixBuffer.makeSilence()

    if (audioStreamQueue.nonEmpty) {
      val (headFramePos, headAudioInputStreamList) = audioStreamQueue.head
      val nextFrame = nLength / getFormat.getFrameSize
      if (headFramePos < framePos) {
        println("Invalid frame position!")
        throw new IOException
      } else if (headFramePos == framePos) {
        // 頭にある場合はそれを audioInputStreamSet に入れる
        audioStreamQueue.dequeue()
        headAudioInputStreamList.foreach(x => {
          audioInputStreamSet.add(x)
        })
        return read(abData, nOffset, nLength)
      } else if (headFramePos > framePos && headFramePos < framePos + nextFrame) {
        // 次の framePos の start 地点で分割
        val nowFramePos = framePos
        val secondHeadFramePos = ((headFramePos - nowFramePos) * getFormat.getFrameSize).toInt
        val secondNLength = ((nowFramePos + nextFrame - headFramePos) * getFormat.getFrameSize).toInt
        val a = read(abData, nOffset, secondHeadFramePos)
        val b = read(abData, nOffset + secondHeadFramePos, secondNLength)
        return if (b == -1) -1 else a + b
      }
    }
    // the calculation below is not scala-like so later would like to be refactored
    // remember the maximum number of samples actually mixed
    val maxMixed = audioInputStreamSet.foldLeft(0) { (max, stream) =>
      val needRead = mixBuffer.getSampleCount * stream.getFormat.getFrameSize
      if (tempBuffer == null || tempBuffer.length < needRead) tempBuffer = new Array[Byte](needRead)
      val bytesRead = stream.read(tempBuffer, 0, needRead)
      if (bytesRead == -1) {
        audioInputStreamSet.remove(stream)
        max
      } else {
        // now convert this buffer to float samples
        readBuffer.initFromByteArray(tempBuffer, 0, bytesRead, stream.getFormat)
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
        if (max < readBuffer.getSampleCount) readBuffer.getSampleCount else max
      }
    }
    framePos += nLength / getFormat.getFrameSize
    if (maxMixed == 0) {
      if (audioInputStreamSet.isEmpty && audioStreamQueue.isEmpty) {
        // nothing mixed, no more streams available: end of stream
        return -1
      }
      // nothing written, but still streams to read from
      return nLength
    }
    mixBuffer.convertToByteArray(0, maxMixed, abData, nOffset, getFormat)
    maxMixed * getFormat.getFrameSize
  }

  /**
    * skips lLengnth from the framePos
    */
  @throws[IOException]
  override def skip(lLength: Long): Long = {
    val skippedMax = audioInputStreamSet.map(_.skip(lLength)).max
    val nextFrame = lLength / getFormat.getFrameSize
    val headFramePosOverLlength = audioStreamQueue.filter(x => x._1 < framePos + nextFrame)
    val skippedMaxInQueue = headFramePosOverLlength.map(x => {
      audioStreamQueue.dequeue()
      val (headFramePos, headAudioInputStreamList) = x
      headAudioInputStreamList.map(y => {
        audioInputStreamSet.add(y)
        val skipped = y.skip(lLength - (headFramePos - framePos))
        skipped + (headFramePos - framePos)
      }).max
    }).max
    framePos += lLength / getFormat.getFrameSize
    Math.max(skippedMax, skippedMaxInQueue)
  }

  /**
    * The minimum of available() of all input stream is calculated and returned.
    */
  @throws[IOException]
  override def available: Int = {
    audioStreamQueue.map(_._2.map(_.available()).min).min
  }

  @throws[IOException]
  override def close() {
    audioStreamQueue.foreach(_._2.map(_.close()))
    audioInputStreamSet.foreach(_.close())
    audioInputStreamSet.foreach(audioInputStreamSet.remove(_))
  }

  /**
    * まだ出来ていない
    */
  override def mark(nReadLimit: Int) {
    // audioInputStreamSet.foreach(_.mark(nReadLimit))
  }

  /**
    * 同上
    */
  @throws[IOException]
  override def reset() {
    // audioInputStreamSet.foreach(_.reset())
  }

  /**
    * 出来ていないので false を返す
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
