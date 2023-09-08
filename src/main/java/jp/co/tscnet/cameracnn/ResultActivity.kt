package jp.co.tscnet.cameracnn

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.io.File
import java.util.regex.Pattern


/**
 * 判定結果画面アクティビティ
 */
@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class ResultActivity : AppCompatActivity() {
    companion object {
        const val IMAGE_PATH = "jp.co.tscnet.cameracnn.IMAGE_PATH"
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        /**
         * 画面部品取得
         */
        val imageView = findViewById<ImageView>(R.id.imageView)
        val resultLabel = findViewById<TextView>(R.id.resultLabel)
        val nutritionLabel = findViewById<TextView>(R.id.nutritionLabel)


        /**
         * 前画面から画像ファイル取得
         */
        val imagePath = intent.getStringExtra(IMAGE_PATH)
        val bitmap = BitmapFactory.decodeFile(imagePath)

        /**
         * 判定実施
         */
        val classifier = Classifier(this@ResultActivity)
        val result = classifier.classifyImageFromPath(imagePath)

        /**
         * 結果表示
         */
        val kind = when (result) {
            1 -> "から揚げ"
            2 -> "オムライス"
            3 -> "カレーライス"
            4 -> "ハンバーグ"
            5 -> "ミートソーススパゲティ"
            else -> "UNKNOWN"
        }
        // 栄養情報を表示
        val nutritionInfo = when (result) {
            1 -> "カロリー: 307kcal, タンパク質: 24.2g, \n脂質: 18.1g, 炭水化物: 13.3g"
            2 -> "カロリー: 627kcal, タンパク質: 28.9g, \n脂質: 29.7g, 炭水化物: 107g"
            3 -> "カロリー: 799kcal, タンパク質: 21.2g,\n 脂質: 26.6g, 炭水化物: 128.8g"
            4 -> "カロリー: 268kcal, タンパク質: 15.9g, \n脂質: 16.1g, 炭水化物: 14.7g"
            5 -> "カロリー: 607kcal, タンパク質: 22.8g, \n脂質: 18.2g, 炭水化物: 97.8g"
            else -> "栄養情報はありません"
        }
        //nutritionLabel.text = nutritionInfo
        val nutritionText = SpannableString(nutritionInfo)
        val pattern = Pattern.compile("カロリー")

        // テキスト中のマッチしたパターンのstartとendのインデックスを見つける
        val matcher = pattern.matcher(nutritionInfo)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            // ブルーのカラースパンをマッチしたパターンに適用する
            nutritionText.setSpan(ForegroundColorSpan(Color.BLUE), start, end, 0)
            nutritionText.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
        }
        nutritionLabel.text = nutritionText

        val label = "「$kind」ですね！"
        resultLabel.text = label
        imageView.setImageBitmap(bitmap)
//
    }
}
