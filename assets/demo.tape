Output "/Users/shivam/Developer/payment-processing-engine/assets/demo.gif"

Set FontSize 13
Set FontFamily "JetBrains Mono"
Set Width 1100
Set Height 640
Set Padding 20
Set Theme "Catppuccin Mocha"
Set WindowBar Colorful
Set LetterSpacing 0

Type "cd /Users/shivam/Developer/payment-processing-engine && clear"
Enter
Sleep 500ms

Type "mvn test -q"
Enter
Sleep 3s

Type "java -cp target/classes com.shivamshahi.payments.Main"
Enter
Sleep 5s
