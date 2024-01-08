import java.io.File
import java.nio.file.Path

object Main {
  case class Config(bmsFile: File = new File("."),
                    soundPath: String = "",
                    outputPath: String = "",
                    outputFilePrefix: String = "output",
                    rane: String = "",
                    bulk: Boolean = false,
                    individual: Boolean = false,
                    includeNotesRane: Boolean = false,
                    bar: String = "",
                    attenuationPerStream: Double = 0.1)

 main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[Config]("BMX_PARA_OUT") {
      head("version", "0.1")
      opt[File]('i', "inputBmsFile").required.action((x, c) => c.copy(bmsFile = x)).text("specify the input BMS File")
      opt[String]('s', "soundInputPath").action((x, c) => c.copy(soundPath = x)).text("specify the input sound File. Default: path of the BMS file") // string じゃなくて Path にしたい
      opt[String]('o', "outputPath").action((x, c) => c.copy(outputPath = x)).text("the output path. Defualt: directory of the input BMS file")
      opt[String]('p', "outputFilePrefix").action((x, c) => c.copy(outputFilePrefix = x)).text("the output file prefix. Default: output")
      opt[String]('r', "rane").action((x, c) => c.copy(rane = x)).text("specify the rane")
      opt[Unit]('b', "bulk").action((x, c) => c.copy(bulk = true)).text("set bulk for all rane. If no rane, set to be true")
      opt[Unit]('d', "individual").action((x, c) => c.copy(individual = true)).text("individual output for each rane")
      opt[Unit]('n', "includeNotesRane").action((x, c) => c.copy(includeNotesRane = true)).text("the flag for each rane include notes")
      opt[String]("bar").action((x, c) => c.copy(bar = x)).text("specify the bar num ")
      opt[Double]('t', "attenuationPerStream").action((x, c) => c.copy(attenuationPerStream = x)).text("attenuation rate for each stream (dB)")
    }

    parser.parse(args, Config()) match {
      case Some(Config(i, s, o, p, r, b, a, n, bar, t)) => BMXParaOutRunner(i, if (s == "") i.getParent else s, if (o == "") i.getParent else o, p, r, if (r == "" && !a) true else b, a, n, bar, t).run()
      case None =>
    }
  }
}
