package Koreatech.grad_project;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

public class TutorialActivity extends AppCompatActivity {
    private final static int totalPageNum = 5;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tutorial_main);

        SharedPreferences infoData;
        ViewPager viewPager;
        ViewPagerAdapter pagerAdapter ;

        viewPager = findViewById(R.id.viewPager) ;
        pagerAdapter = new ViewPagerAdapter(this) ;
        viewPager.setAdapter(pagerAdapter);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            throw new NullPointerException("Null ActionBar");
        } else {
            actionBar.hide();
        }

        infoData = getSharedPreferences("infoData", MODE_PRIVATE);
        SharedPreferences.Editor editor = infoData.edit();  //튜토리얼 봤음을 저장
        editor.putBoolean("IS_SHOW_TUTORIAL", true);
        editor.apply();
        Log.d("Debug:IS_SHOW_TUTORIAL", infoData.getBoolean("IS_SHOW_TUTORIAL", false) + "");
    }

    class ViewPagerAdapter extends PagerAdapter {
        // LayoutInflater 서비스 사용을 위한 Context 참조 저장.
        private Context context ;

        // Context를 전달받아 저장하는 생성자 추가.
        ViewPagerAdapter(Context context) {
            super();
            this.context = context ;
        }

        @Override
        @NonNull
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View view = null;

            ImageView img;
            Button but;

            if (context != null) {
                // LayoutInflater를 통해 각 xml파일을 뷰로 생성.
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                switch(position) {
                    case 0:
                        view = inflater.inflate(R.layout.tutorial_page1, container, false);
                        img = view.findViewById(R.id.tutorial_image) ;
                        img.setImageResource(R.drawable.tutorial1);
                        break;
                    case 1:
                        view = inflater.inflate(R.layout.tutorial_page1, container, false);
                        img = view.findViewById(R.id.tutorial_image) ;
                        img.setImageResource(R.drawable.tutorial2);
                        break;
                    case 2:
                        view = inflater.inflate(R.layout.tutorial_page1, container, false);
                        img = view.findViewById(R.id.tutorial_image) ;
                        img.setImageResource(R.drawable.tutorial3);
                        break;
                    case 3:
                        view = inflater.inflate(R.layout.tutorial_page1, container, false);
                        img = view.findViewById(R.id.tutorial_image) ;
                        img.setImageResource(R.drawable.tutorial4);
                        break;
                    case 4:
                        view = inflater.inflate(R.layout.tutorial_page2, container, false);
                        but = view.findViewById(R.id.end_button);
                        but.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                finish();
                            }
                        });
                        break;
                }
            }
            // 뷰페이저에 추가.
            container.addView(view) ;

            return view ;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, @NonNull Object object) {
            // 뷰페이저에서 삭제.
            container.removeView((View) object);
        }

        @Override
        public int getCount() {
            return totalPageNum;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }
}

