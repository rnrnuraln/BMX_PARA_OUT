package dao

import java.io.File
import java.io.IOException

import javax.sound.sampled.{AudioFileFormat, AudioFormat, AudioInputStream, AudioSystem}

/**
 * AudioInputStream を一度に open しすぎると負荷がかかる
 * 使い終わった AudioInputStream は close するようにして、 open する AudioInputStream を制限する
 *
 * @param audioFilePath
 */
case class AudioFileHandler(audioFilePath: String) {
  def getAudioInputStream: AudioInputStream = {
    AudioFileHandler.createAudioInputStream(audioFilePath)
  }

  def available(): Int = {
    val audioInputStream = AudioFileHandler.createAudioInputStream(audioFilePath)
    val available = audioInputStream.available()
    audioInputStream.close()
    available
  }

  def getFormat: AudioFormat = {
    val audioInputStream = AudioFileHandler.createAudioInputStream(audioFilePath)
    val format = audioInputStream.getFormat
    audioInputStream.close()
    format
  }

  def getFrameLength: Long = {
    val audioInputStream = AudioFileHandler.createAudioInputStream(audioFilePath)
    val frameLength = audioInputStream.getFrameLength
    audioInputStream.close()
    frameLength
  }

}

object AudioFileHandler {

  /**
    * wav 以外も対応出来るようにするつもりだったが、ゴミみたいな音質のものしか出てこないので実質使えなかった
    * なので、今のところは実質 wav だけ対応
    * @param filePath
    * @return
    */
  def createAudioInputStream(filePath: String): AudioInputStream = {
    val fileWithoutExtention = filePath.split("\\.").dropRight(1).reduce(_ + "." + _)
    val wavFile = new File(fileWithoutExtention + ".wav")
    if (wavFile.exists()) {
      AudioSystem.getAudioInputStream(wavFile)
    } else {
      val oggFile = new File(fileWithoutExtention + ".ogg")
      val in: AudioInputStream = if (oggFile.exists()) {
        AudioSystem.getAudioInputStream(oggFile)
      } else {
        throw new IOException("Unsupported Audio or No File: " + fileWithoutExtention + "....")
      }
      in.reset()
      val baseFormat = in.getFormat
      // println("format: " + baseFormat)
      val a = AudioSystem.getTargetEncodings(baseFormat)
      // println("fawfe: " + a)
      val targetFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, baseFormat.getSampleRate, 16, baseFormat.getChannels, baseFormat.getChannels * 2, baseFormat.getSampleRate, false)
      // println("faweff " + AudioSystem.isConversionSupported(targetFormat, in.getFormat))
      val out = AudioSystem.getAudioInputStream(targetFormat, in)
      // println("out available: " + out.available())
      // println("out frame length: " + out.getFrameLength())
      out
    }
  }
}

