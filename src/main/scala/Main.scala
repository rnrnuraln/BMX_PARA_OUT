import Sandbox.JustTry

object Main {

  def main(args: Array[String]): Unit = {
    JustTry.trying(args(0), args(1), args(2))
  }
}
