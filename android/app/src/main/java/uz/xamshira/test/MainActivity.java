package uz.xamshira.test;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.WindowManager;
import android.webkit.WebSettings;
import androidx.annotation.Nullable;
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
    }

    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onPostCreate(savedInstanceState, persistentState);
        // Bu yerda, WebView'ning o'zi TO'LIQ tayyor bo'ladi (onCreate'da
        // hali tayyor bo'lmasligi mumkin edi). capacitor.config.json'dagi
        // "allowMixedContent" sozlamasi native darajada yetarlicha
        // qo'llanilmagani uchun, buni shu yerda, to'g'ridan-to'g'ri
        // majburlab qo'yamiz — ilova (https kontekst) backend serverga
        // oddiy (http) so'rov yubora olishi uchun zarur.
        if (getBridge() != null && getBridge().getWebView() != null) {
            WebSettings settings = getBridge().getWebView().getSettings();
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
    }
}
