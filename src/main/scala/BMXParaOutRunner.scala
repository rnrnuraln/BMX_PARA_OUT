import java.io.File

import audioinputstreams.ArrangedMixingAudioInputStream
import bmsreading.BMSFileReader
import javax.sound.sampled.{AudioFileFormat, AudioSystem}

/**
  * CUI エンドポイント的なやつ
  */
case class BMXParaOutRunner(bmsFile: File, soundPath: String, outputPath: String, outputFilePrefix: String, rane: String,
                            bulkAll: Boolean, individual: Boolean, includeNotesRane: Boolean, barString: String,
                            attenuationPerStream: Double) {
  require(!(bulkAll & individual), "both bulk and individual set!")

  val includeNotesFlag: Boolean = bulkAll || includeNotesRane
  private val barSeq: Seq[Int] = {
    if (barString == "")
      Seq()
    else {
      val barSeq = barString.split("-")
      if (barSeq.length == 1) {
        Seq(barSeq(0).toInt)
      } else {
        (barSeq(0).toInt to barSeq(1).toInt)
      }
    }
  }

  def run(): Unit = {
    val bmsFileReader = new BMSFileReader(bmsFile, soundPath)
    val audioFormat = bmsFileReader.getAudioFormat
    val raneNum = bmsFileReader.getRaneNum
    val raneSeq = getRaneSeq(raneNum)
    val bmsLocationForEachRane = raneSeq.map(bmsFileReader.read(_, includeNotesFlag, barSeq))
    raneSeq.zip(bmsLocationForEachRane).foreach(x => {
      if (x._2.length == 0) {
        println("No input file with this rane. Skip writing wav file.")
      } else {
        val mixer = new ArrangedMixingAudioInputStream(audioFormat, x._2, attenuationPerStream.toFloat)
        val outputRaneString = {
          if (bulkAll)
            ""
          else if (x._1.length == 1)
            "_" + (x._1.head + 1)
          else
            "_" + (x._1.head + 1) + "-" + (x._1.last + 1)
        }
        println("bms notes for rane" + (if (bulkAll) " all" else outputRaneString) + ": " + x._2.length)
        val outputFileName = outputFilePrefix + outputRaneString + ".wav"
        val outputFileString = utils.Util.concatFilePath(outputPath, outputFileName)
        val outputFile = new File(outputFileString)
        AudioSystem.write(mixer, AudioFileFormat.Type.WAVE, outputFile)
      }
    })
  }

  private def getRaneSeq(raneNum: Int): Seq[Seq[Int]]= {
    if (rane == "") {
      if (bulkAll) {
        Seq((0 until raneNum))
      } else if (individual) {
        (0 until raneNum).map(Seq(_))
      } else {
        Seq()
      }
    } else {
      rane.split(",").map(x => {
        val splittedByHyphen = x.split("-")
        if (splittedByHyphen.length == 1)
          Seq(splittedByHyphen(0).toInt - 1)
        else
          (splittedByHyphen(0).toInt to splittedByHyphen(1).toInt).map(_ - 1).toSeq
      })
    }
  }

}
