package Sandbox

import javax.sound.sampled._
import java.io.File
import audioinputstreams.ArrangedMixingAudioInputStream

object JustTry {
  def trying(file1: String, file2: String, output: String) {
    val soundFile1 = new File(file1)
    val soundFile2 = new File(file2)

    val audioInputStream = AudioSystem.getAudioInputStream(soundFile1)
    val audioInputStream2 = AudioSystem.getAudioInputStream(soundFile2)
    val audioInputStream3 = AudioSystem.getAudioInputStream(soundFile2)
    val audioInputStream4 = AudioSystem.getAudioInputStream(soundFile1)
    val list = Seq((44100L * 1, Seq(audioInputStream3)), (0L, Seq(audioInputStream, audioInputStream2)), (44100L * 180, Seq(audioInputStream4)));

    val audioFormat = audioInputStream.getFormat
    val mixer = new ArrangedMixingAudioInputStream(audioFormat, list);

    AudioSystem.write(mixer, AudioFileFormat.Type.WAVE, new
        File(output)
    );
  }
}
