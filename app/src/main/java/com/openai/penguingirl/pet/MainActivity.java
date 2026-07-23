package com.openai.penguingirl.pet;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionState();
    }

    private ScrollView buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(255, 247, 232));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(30), dp(28), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView preview = new ImageView(this);
        preview.setImageResource(R.drawable.app_icon);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(preview, new LinearLayout.LayoutParams(dp(190), dp(190)));

        TextView title = new TextView(this);
        title.setText("企鹅女孩桌宠 2.6");
        title.setTextSize(27);
        title.setTextColor(Color.rgb(27, 29, 34));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(4);
        root.addView(title, titleParams);

        TextView description = new TextView(this);
        description.setText("25 帧完整角色整帧步态 · 支持边缘藏身\n长按宠物即可投喂金色小零食");
        description.setTextSize(15);
        description.setTextColor(Color.rgb(91, 94, 102));
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams descriptionParams = matchWrap();
        descriptionParams.topMargin = dp(10);
        descriptionParams.bottomMargin = dp(20);
        root.addView(description, descriptionParams);

        Button permission = makeButton("① 授权悬浮窗", true);
        permission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openOverlayPermission();
            }
        });
        root.addView(permission, buttonParams());

        Button start = makeButton("② 启动桌宠", true);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startPet();
            }
        });
        root.addView(start, buttonParams());

        Button stop = makeButton("停止桌宠", false);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopService(new Intent(MainActivity.this, PetService.class));
                statusView.setText("桌宠已停止");
            }
        });
        root.addView(stop, buttonParams());

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setTextColor(Color.rgb(172, 112, 0));
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(17);
        root.addView(statusView, statusParams);

        TextView help = new TextView(this);
        help.setText("桌宠会随机选择屏幕内的新目标，可横向、纵向或斜向移动。\n连续巡游一段时间后会停下、趴下并播放呼吸待机动画。\n拖到手机左边框或右边框并松手，宠物会藏起来只露半个头；点击露出的头即可回来。\n长按角色会出现“投喂”按钮，投喂后宠物会开心跳跃。\n拖动角色可移动位置，轻点角色也会播放开心姿态，点击 × 关闭。\n运行期间会保留一条低优先级通知，防止系统随意结束桌宠。");
        help.setTextSize(13);
        help.setTextColor(Color.rgb(116, 118, 124));
        help.setGravity(Gravity.CENTER);
        help.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams helpParams = matchWrap();
        helpParams.topMargin = dp(18);
        root.addView(help, helpParams);
        return scroll;
    }

    private void openOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "悬浮窗权限已经开启", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void startPet() {
        if (!Settings.canDrawOverlays(this)) {
            statusView.setText("请先授予“显示在其他应用上层”权限");
            openOverlayPermission();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 200);
        }
        Intent intent = new Intent(this, PetService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        statusView.setText("桌宠已启动，正在返回桌面…");
        Toast.makeText(this, "企鹅女孩开始巡游啦", Toast.LENGTH_SHORT).show();
        statusView.postDelayed(new Runnable() {
            @Override
            public void run() {
                moveTaskToBack(true);
            }
        }, 550);
    }

    private void refreshPermissionState() {
        if (statusView == null) return;
        statusView.setText(Settings.canDrawOverlays(this)
                ? "悬浮窗权限：已授权"
                : "悬浮窗权限：尚未授权");
    }

    private Button makeButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.rgb(25, 26, 30) : Color.WHITE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? Color.rgb(255, 195, 46) : Color.rgb(44, 47, 55));
        background.setCornerRadius(dp(16));
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        params.topMargin = dp(11);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
