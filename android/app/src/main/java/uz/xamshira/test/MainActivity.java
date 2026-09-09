package uz.xamshira.test;

import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.WebSettings;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Skrinshot va ekran yozib olishni bloklaydi, shuningdek so'nggi
        // ilovalar ro'yxatida (recent apps) mazmunni yashiradi — test
        // savol/javoblarining tarqalishining oldini olish uchun.
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );
        // capacitor.config.json'dagi "allowMixedContent" o'zi native darajada
        // yetarlicha qo'llanilmasligi mumkin — shuning uchun, WebView sozlamasini
        // to'g'ridan-to'g'ri, kod orqali ham majburlab qo'yamiz. Bu, ilova
        // xavfsiz (https) kontekstda ishlab, backend serverga oddiy (http)
        // so'rov yuborishi uchun zarur.
        if (this.bridge != null && this.bridge.getWebView() != null) {
            WebSettings settings = this.bridge.getWebView().getSettings();
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
    }
}
