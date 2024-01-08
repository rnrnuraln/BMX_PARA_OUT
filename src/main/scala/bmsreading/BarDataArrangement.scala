package bmsreading

/**
  * 小節ごとにデータを配置
  *
  * @param soundSeq 配置を保存 (何ビート目かと BMS 定義の soundName の組) の Array
  * @param bpmMap   BPM の変わるタイミングとその値を保存
  */
case class BarDataArrangement(soundSeq: Array[Map[Double, String]], bpmMap: Map[Double, Double], signature: Double, notesRaneSeq: Seq[(Double, String)]) {

  private val sortedBPMSeq: List[(Double, Double)] = bpmMap.toList.sortBy(_._1)

  private def bpmToFramePerBeat(bpm: Double, framePerSecond: Double): Double = {
    60 * framePerSecond / bpm
  }

  // 拍を frame に変換
  private def getFrameNum(targetBeat: Double, framePerSecond: Double, initFramePos: Double, initBPM: Double): Double = {
    val bpmSeqWithLastBPM = ((4 * signature, getLastBPM(initBPM)) :: (sortedBPMSeq.reverse)).reverse
    bpmSeqWithLastBPM.foldLeft((initFramePos.toDouble, 0.0, initBPM)) { (s, bpmBeat) =>
      val beat = bpmBeat._1
      val bpm = bpmBeat._2
      val postFrame = s._1
      val prevBeat = s._2
      val prevBPM = s._3
      if (targetBeat < prevBeat) {
        (postFrame, beat, bpm)
      } else if (targetBeat < beat) {
        (postFrame + bpmToFramePerBeat(prevBPM, framePerSecond) * (targetBeat - prevBeat), beat, bpm)
      } else {
        (postFrame + bpmToFramePerBeat(prevBPM, framePerSecond) * (beat - prevBeat), beat, bpm)
      }
    }._1
  }

  // その小節の, 音ごとのフレーム単位での start point を作成 Seq(フレーム, Seq(音定義)) を作成
  def makeFramePosAudioNameList(initBPM: Double, rane: Seq[Int], framePerSecond: Double, initFramePos: Double, includeNotesRane: Boolean = false): List[(Long, String)] = {
    val bgmRaneSeq = rane.foldLeft(List(): List[(Long, String)]) { (list, i) =>
      if (soundSeq.length <= i)
        list
      else {
        soundSeq(i).foldLeft(list) { (list2, p) =>
          val beatNum = p._1
          val soundName = p._2
          val frameNum = getFrameNum(beatNum, framePerSecond, initFramePos, initBPM).round
          (frameNum, soundName) :: list2
        }
      }
    }
    if (!includeNotesRane || notesRaneSeq.isEmpty) {
      bgmRaneSeq
    } else {
      notesRaneSeq.foldLeft(bgmRaneSeq) { (list2, p) =>
        val beatNum = p._1
        val soundName = p._2
        val frameNum = getFrameNum(beatNum, framePerSecond, initFramePos, initBPM).round
        (frameNum, soundName) :: list2
      }
    }
  }

  // 最終BPMを返す
  def getLastBPM(initBPM: Double): Double = {
    if (sortedBPMSeq.nonEmpty) sortedBPMSeq.last._2 else initBPM
  }

  // 小節全体のフレーム数を返す
  def getLastFramePos(framePerSecond: Double, initPerFramePos: Double, initBPM: Double): Double =
    getFrameNum(4 * signature, framePerSecond, initPerFramePos, initBPM)
}

object BarDataArrangement {
  // 特定の小節だけを集めた line からその小節のデータを収集
  def apply(input: Seq[String], longNotesMap: scala.collection.mutable.Map[String, Boolean] = scala.collection.mutable.Map(), extensionBPMMap: Map[String, Double]): BarDataArrangement = {
    // 要らないところをカット
    val processedInput = input.map(_.drop(4))
    val signatureList = processedInput.filter(_.startsWith("02"))
    val signature = if (signatureList.nonEmpty) signatureList.last.split(":")(1).toDouble else 1.0
    val beatPerBar = signature * 4.0
    val bpmSeq = processedInput.filter(_.matches("^0[38].*")).foldLeft(Map(): Map[Double, Double]) { (map, x) =>
      val isExtensionBPM = x.startsWith("08")
      val line = x.split(":")(1)
      val soundDefLen = line.length / 2
      val beatPerLoc = 4 * signature / soundDefLen
      (0 until soundDefLen).foldLeft(map) { (map2, i) =>
        val bpmDef = line.slice(2 * i, 2 * (i + 1))
        val bpm = if (isExtensionBPM) extensionBPMMap.getOrElse(bpmDef, 0.0) else Integer.parseInt(bpmDef, 16).toDouble
        if (bpm == 0.0)
          map2
        else {
          val beatNum = beatPerLoc * i
          map2 + (beatNum -> bpm)
        }
      }
    }
    val soundSeq = processedInput.filter(_.startsWith("01")).map(x => {
      val line = x.split(":")(1)
      val soundDefLen = line.length / 2
      val beatPerLoc = 4 * signature / soundDefLen
      (0 until soundDefLen).foldLeft(Map(): Map[Double, String]) { (map, i) =>
        val bmsDef = line.slice(2 * i, 2 * (i + 1))
        if (bmsDef == "00")
          map
        else {
          val beatNum = beatPerLoc * i
          map + (beatNum -> bmsDef)
        }
      }
    }).toArray
    // BGM ではなく叩くところを集める。とりあえず SP だけ対応。
    // 出来ればここのデータ構造は何とかしたいところ……
    val notesRaneSoundSeq = processedInput.filter(_.matches("^[1256].*")).foldLeft(List(): List[(Double, String)]) { (list, x) =>
      val line = x.split(":")(1)
      val soundDefLen = line.length / 2
      val beatPerLoc = 4 * signature / soundDefLen
      val bmsRane = x.slice(0, 2)
      val tmpList = (0 until soundDefLen).foldLeft(List(): List[(Double, String)]) { (innerList, i) =>
        val bmsDef = line.slice(2 * i, 2 * (i + 1))
        if (bmsDef == "00")
          innerList
        else if (x.startsWith("5") || x.startsWith("6")) {
          // bmsRane の long note の flag が立っていたら何もせず、フラグを引き下げる
          if (longNotesMap.getOrElse(bmsRane, false)) {
            longNotesMap += (bmsRane -> false)
            innerList
          } else {
            longNotesMap += (bmsRane -> true)
            val beatNum = beatPerLoc * i
            (beatNum, bmsDef) :: innerList
          }
        } else {
          val beatNum = beatPerLoc * i
          (beatNum, bmsDef) :: innerList
        }
      }
      tmpList ::: list
    }
    BarDataArrangement(soundSeq, bpmSeq, signature, notesRaneSoundSeq)
  }
}
