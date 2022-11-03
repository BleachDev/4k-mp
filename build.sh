rm -r out
javac -d ./out M.java
cd out
mkdir ./META-INF
echo "Manifest-Version: 1.0
Class-Path: .
Main-Class: M
" >> ./META-INF/MANIFEST.MF
zip -r9 ./../4K.jar *
