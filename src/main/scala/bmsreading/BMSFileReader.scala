package bmsreading

import javax.sound.sampled._
import java.io.File
import utils.Util

import dao.AudioFileHandler

/**
 * BMS ファイルを読んで、各音源ファイルを鳴らす時間をレーンごとに BarDataArrangement で設定
 * BGM 側はレーンごとに書き出せるが、ノーツ側は レーン丸ごと書き出してしまうことに注意
 * (この辺りの仕様はいまいち使い勝手が悪いのでどうにかしたい)
 *
 * @param bmsFilePath
 * @param wavFolderPath
 */
class BMSFileReader(val bmsFilePath: File, val wavFolderPath: String) {

  lazy val soundNameFilePathMap: Map[String, String] = {
    val sc = scala.io.Source.fromFile(bmsFilePath, "Shift_JIS")
    val lines = sc.getLines.toSeq

    lines.filter(_.startsWith("#WAV")).map(x => {
      val y = x.split("#WAV")(1).split(" ")
      val soundName = y(0)
      val fileName = y.drop(1).reduce(_ + " " + _).trim()
      val filePath = Util.concatFilePath(wavFolderPath, fileName)
      (soundName, filePath)
    }).toMap
  }

  lazy val eachBarData: Seq[BarDataArrangement] = {
    val sc = scala.io.Source.fromFile(bmsFilePath, "Shift_JIS")
    val lines = sc.getLines.toSeq

    // main データ groupBy する
    val linesGroupByBar: Map[Int, Seq[String]] = lines.filter(_.matches("^#[0-9]{5}:.*")).groupBy(_.slice(1, 4).toInt)

    // 拡張BPM用のデータ
    val extensionBPMMap = lines.filter(_.matches("^#BPM[0-9A-F]{2} .*")).map(x => {
      val bpmName = x.slice(4, 6)
      val bpm = x.split(" ").last.toDouble
      (bpmName, bpm)
    }).toMap

    // bar data を bar ごとに収集
    val initBar = linesGroupByBar.keys.min
    val lastBar = linesGroupByBar.keys.max
    val longNotesMap: scala.collection.mutable.Map[String, Boolean] = scala.collection.mutable.Map()
    (initBar to lastBar).map(i => {
      linesGroupByBar.get(i) match {
        case Some(s) => BarDataArrangement(s, longNotesMap, extensionBPMMap)
        case _ => BarDataArrangement(Array(), Map(), 1.0, List())
      }
    })
  }

  def read(rane: Seq[Int], includeNotesRane: Boolean, barSeq: Seq[Int]): Seq[(Long, AudioFileHandler)] = {
    val sc = scala.io.Source.fromFile(bmsFilePath, "Shift_JIS")
    val lines = sc.getLines.toSeq
    val initBpm = lines.filter(_.startsWith("#BPM ")).reverse(0).split("#BPM ").last.toDouble

    val framePerSecond = getAudioFormat.getFrameRate

    // eachBarData の始点
    val minBar: Int = lines.filter(_.matches("^#[0-9]{5}:.*")).map(_.slice(1, 4).toInt).min
    val barToUse = if (barSeq.isEmpty) eachBarData else barSeq.map(x => eachBarData(x - minBar))

    val frameAudioNameList = barToUse.foldLeft(0.0, initBpm, List(): List[(Long, String)]) { (s, barData) =>
      val framePos = s._1
      val bpm = s._2
      val prevFrameAudioList = s._3
      val barFrameAudioList = barData.makeFramePosAudioNameList(bpm, rane, framePerSecond, framePos, includeNotesRane)
      val nextFramePos = barData.getLastFramePos(framePerSecond, framePos, bpm)
      val nextBPM = barData.getLastBPM(bpm)
      (nextFramePos, nextBPM, prevFrameAudioList ::: barFrameAudioList)
    }._3

    frameAudioNameList.map(x => {
      soundNameFilePathMap.get(x._2) match {
        case Some(filePath) => (x._1, AudioFileHandler(filePath))
        case None => (-1L, AudioFileHandler(""))
      }
    }).filter(_._1 >= 0L)
  }

  def getAudioFormat: AudioFormat = {
    val filePath = soundNameFilePathMap.values.toSeq.head
    val audioHandler = AudioFileHandler(filePath)
    audioHandler.getFormat
  }

  def getRaneNum: Int = {
    eachBarData.map(_.soundSeq.length).max
  }
}

object BMSFileReader {

}