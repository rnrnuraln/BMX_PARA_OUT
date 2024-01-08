package utils
import java.io.File

object Util {
  def concatFilePath(directoryPath: String, fileName: String): String = {
    val separator = File.separator
    if (directoryPath.endsWith(separator)) directoryPath + fileName else directoryPath + separator + fileName
  }

}
