BMX_PARA_OUT
===

## Description

BMX ファイルの定義にしたがって， BPM レーンごとにパラアウトを出力するツール
BMS の音分析とかに使ってください。

ArrangedMixingAudioInputStream.scala は， こちらの実装を参考にしました  
https://github.com/leif81/jsresources-examples/blob/master/src/main/java/org/jsresources/audioconcat/MixingFloatAudioInputStream.java

## Usage

xxx 以下にある yyy.bms をレーンごとにパラアウトしたい場合
```
run -i xxx/yyy.bms -d
```

bulk で output_path ディレクトリ以下に all_output.wav という名前で保存したい場合
```
run -i xxx/yyy.bms -o output_path -b -p all_output
```

オプション一覧
```
-i, --inputBmsFile <value> specify the input BMS File
-s, --soundInputPath <value> specify the input sound File. Default: path of the BMS file
-o, --outputPath <value> the output path. Defualt: directory of the input BMS file
-p, --outputFilePrefix <value> the output file prefix. Default: output
-r, --rane <value>       specify the rane
-b, --bulk               set bulk output for all rane. If no rane specified, set to be true
-d, --individual         individual output for each rane
-n, --includeNotesRane   the flag for each rane include notes
--bar <value>            specify the bar num
-t, --attenuationPerStream <value> attenuation rate for each stream (dB)
```

基本的に BGM レーンのみ対応
ノーツレーンを入れたい場合は -n オプションで入れられるが、全てのレーンに入ってしまうことに注意

## Example using python

この scala コードだけだとただ単純に BMS ファイルからパラアウトを出力することしか出来ない。  
なので、使用している beatoraja の songdata.db から BMS を検索して手元に結果を出力する参考コードを載せておく。  
`example_python/` 以下に python の参考スクリプトが置いてある。  
検索には sqlite を用いている。  
検索したい曲名が XXXX なら、以下のように曲名を指定して python スクリプトを走らせる
```
python para_out.py XXXX
```
あとは指示に従って出力したい BMS ファイルを指定すれば、それぞれのレーン単位でパラアウトが出力される

## 今後やりたいこと

正直 bulk と individual 以外は不要に感じるので削除してしまった方が良いかもしれない (オプションをスリム化したい)  
レーンとか小節の指定については BMS 側をいじって別名で保存して……みたいな感じにした方がむしろ小回りが効きそう。

あとノーツレーン周りの仕様が割と分かりづらくて厳しい気持ちなので、そこも何とかしたいところ

他の人にも使ってもらいたいので、出来れば GUI ツールを用意したい
とはいえ CUI の要素もそれはそれで残しておきたい
とはいえまず先に全体の実装を整理する方が先
