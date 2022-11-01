rm -r out
javac -d ./out M.java
mkdir ./out/META-INF
echo "Manifest-Version: 1.0
Class-Path: .
Main-Class: M
" >> ./out/META-INF/MANIFEST.MF
cd out
zip -r9 ./../4K.jar *