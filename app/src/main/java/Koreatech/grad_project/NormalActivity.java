package Koreatech.grad_project;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;

import net.daum.mf.map.api.MapPOIItem;
import net.daum.mf.map.api.MapPoint;
import net.daum.mf.map.api.MapView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class NormalActivity extends AppCompatActivity implements MapView.MapViewEventListener,
                                                                      MapView.POIItemEventListener,
                                                                      MapView.CurrentLocationEventListener {
    private SharedPreferences infoData;
    private Long startDate;

    private String[] exhibitionState = new String[6];      // 전시관 오픈 여부(1 : open, 0 : close)
    private String[] exhibitionQrCode = new String[6];     // 전시관 QR코드 URL
    private boolean[] isCheckQrArr = new boolean[6];            //전시관 QR코드 확인 체크

    private ViewGroup mapViewContainer;   //맵
    private TextView listCountTv;          //리스트뷰 Count
    private ListView questView;             //관람확인 리스트
    private ImageButton but_gps;            //GPS 버튼

    /*튜토리얼 관람여부*/
    private boolean isShowTutorial;

    /*GPS*/
    MapPOIItem markerGps = new MapPOIItem();
    double longitude;  //위도
    double latitude;   //경도
    private boolean ToggleGps = false;
    private boolean checkInLocation = false;

    /*맵 마커*/
    private MapView mapView;
    private int Mode = 0;
    Bitmap gps_tracking;
    Bitmap[] in_exhibition_marker = new Bitmap[5];
    Bitmap[] out_exhibition_marker = new Bitmap[12];
    Bitmap[] facilities_market = new Bitmap[13];
    Bitmap[] store = new Bitmap[10];

    int zoomLevel = -2;

    /*서비스 통신*/
    public NotiService ms;
    boolean isService = false;
    public ServiceConnection conn = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            // 서비스와 연결되었을 때 호출되는 메서드
            NotiService.NotiBinder mb = (NotiService.NotiBinder) service;
            ms = mb.getService();
            isService = true; // 실행 여부를 판단
        }
        public void onServiceDisconnected(ComponentName name) {
            // 서비스와 연결이 끊기거나 종료되었을 때
            isService = false;
        }
    };
    Timer timer = new Timer();
    TimerTask onMessageTimerTask = new TimerTask() {
        @Override
        public void run() {
            onMessage();
        }
    };

    /*List View*/
    private static final int ImgNumByExhibit = 2;                    //각 전시관 사진 갯수
    private boolean[][] isCheckImgArr = new boolean[6][ImgNumByExhibit];  //전시관 사진 확인 체크
    private int nowPosition;        //선택한 list 위치
    private List<questViewItems> questItems = new ArrayList<>();
    private int questNum;
    private  QuestAdapter adapter;
    ListView listView;
    List<String> qTitle = new ArrayList<>();
    String Title[] = {"제1전시관 겨레의뿌리", "제2전시관 겨례의시련", "제3전시관 겨레의함성", "제4전시관 평화누리", "제5전시관 나라되찾기", "제6전시관 새나라세우기"};
    List<String> qDescription = new ArrayList<>();
    String qrDescription = "해당 전시관 출입구에 있는 QR코드를 찍으세요.";
    String exhibitName[][] = new String[6][ImgNumByExhibit]; //전시물 이름
    Bitmap exhibitImg[][] = new Bitmap[6][ImgNumByExhibit];  //전시물 사진
    List<Bitmap> qImages = new ArrayList<>();
    Bitmap qrImages;

    List<Integer> qExhibitionNum = new ArrayList<>();
    List<Integer> qType = new ArrayList<>();     //0 - QR / 1 - Image
    List<Integer> qNumOfImg = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_normal);
        mapViewContainer = findViewById(R.id.map_view);
        listCountTv = findViewById(R.id.list_count_tv);
        questView = findViewById(R.id.questList);
        but_gps = findViewById(R.id.but_gps);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            throw new NullPointerException("Null ActionBar");
        } else {
            actionBar.hide();
        }

        qrImages = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.qr_img);
        infoData = getSharedPreferences("infoData", MODE_PRIVATE);
        Intent intent = getIntent();
        startDate = intent.getLongExtra("Time", 0);
        getExhibitionData();  // DB 전시관 데이터 받아오기(오픈여부, 전시관 별 QR코드)

        loadInfo();           // 저장된 값 가져오기 - 각 전시관 QR코드 체크 여부, 튜토리얼 여부

        /*튜토리얼*/
        RelativeLayout bt_back_layout = findViewById(R.id.bt_back_layout);
        bt_back_layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        if (!isShowTutorial) {       //처음 지도를 보는 거면(튜토리얼을 본 적이 없으면) 튜토리얼을 보여줌
            intent = new Intent(NormalActivity.this, TutorialActivity.class);
            startActivityForResult(intent, 3000);
        }

        /*서비스 통신*/
        timer.schedule(onMessageTimerTask, 0, 1500);
        Intent intentService = new Intent();
        intentService.setClassName(this, "Koreatech.grad_project.NotiService");
        bindService(intentService ,conn, Context.BIND_AUTO_CREATE);

        /*리스트*/
        listView = findViewById(R.id.questList);

        attributeSetting();
        QuestItemSet();
        adapter = new QuestAdapter(this, questItems);
        listView.setAdapter(adapter); // 리스트뷰 생성됨
        QuestviewClick();

        /*listCountTv 설정 */
        if(questItems.size() == 0) {
            listCountTv.setVisibility(View.GONE);
        }
        else {
            String temp = questItems.size() + "";
            listCountTv.setText(temp);
            listCountTv.setVisibility(View.VISIBLE);
        }

        /*MapView 초기화*/
        initBitmap();
        mapView = new MapView(this);
        mapView.setPOIItemEventListener(this);
        mapView.setMapViewEventListener(this);
        mapViewContainer.addView(mapView);
        ImageButton imageButton = findViewById(R.id.in_exhibition);
        imageButton.performClick();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.cancel();
        unbindService(conn);
    }

    //보낸 Intent 정보 받음
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {   //TutorialActivity에서 돌아왔을 때
                case 3000:
                    break;
                case 49374: {  //QrActivity에서 돌아왔을 때
                    String qrUrl = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent).getContents();
                    //URL 비교
                    boolean isCheck = false;
                    for(int i = 0 ; i < 6 ; i++) {
                        if(exhibitionQrCode[i].equals(qrUrl)) {
                            if(isCheckQrArr[i]) {
                                Toast.makeText(getApplicationContext(), "이미 찍은 QR코드 입니다.", Toast.LENGTH_LONG).show();
                            }
                            else {
                                Toast.makeText(getApplicationContext(), (i + 1) + "전시관의 QR코드와 일치합니다!", Toast.LENGTH_LONG).show();
                                SharedPreferences.Editor editor = infoData.edit();  //해당 전시관에 대한 정보를 공유변수에 저장
                                editor.putBoolean("IS_CHECK_QR_" + (i + 1), true);
                                editor.apply();
                                isCheckQrArr[i] = true;

                                for(int j = 0 ; j < questItems.size() ; j++) { //Listview에서 해당 Qr 제거해줌
                                    questViewItems q = questItems.get(j);
                                    if(q.QTypeItem == 0 && q.QExhibitionNum == i) { //전시관 번호가 같고 QR이면
                                        questItems.remove(q);                   //해당 item 제거
                                        adapter = new QuestAdapter(NormalActivity.this, questItems);
                                        listView.setAdapter(adapter);
                                        String temp = questItems.size() + "";
                                        listCountTv.setText(temp);
                                        if(questItems.size() == 0) {
                                            listCountTv.setVisibility(View.GONE);
                                        }
                                        else {
                                            listCountTv.setVisibility(View.VISIBLE);
                                        }

                                    }
                                }
                            }
                            isCheck = true;
                            break;
                        }
                    }
                    if(!isCheck) {
                        Toast.makeText(getApplicationContext(), "일치하는 QR코드가 존재하지 않습니다.", Toast.LENGTH_LONG).show();
                    }
                    break;
                }
                case 1000: {
                    if(intent.getBooleanExtra("isSuccess", false)) {
                        //해당 전시관의 사진이 전부 찍혔는지
                        boolean isAllPicChecked = true;
                        int nowExhibitionNum = questItems.get(nowPosition).QExhibitionNum;  //체크된 사진의 전시관만 체크하기
                        int nowImgNum = questItems.get(nowPosition).QNumOfImg;
                        isCheckImgArr[nowExhibitionNum][nowImgNum] = true;     //사진 체크됬다고 표시
                        if(exhibitionState[nowExhibitionNum].equals("1") && nowExhibitionNum != 3) {    //연 전시관이고 4전시관 아닐때 체크
                            for(int j = 0 ; j < ImgNumByExhibit ; j++) {
                                isAllPicChecked &= isCheckImgArr[nowExhibitionNum][j];     //비트연산자 사용
                            }
                        }
                        //Log.d("isAllPicChecked", isAllPicChecked + "");
                        if(isAllPicChecked) {
                            SharedPreferences.Editor editor = infoData.edit();
                            editor.putBoolean("IS_CHECK_PIC_" + (nowExhibitionNum + 1), true);
                            editor.apply();
                        }
                        questItems.remove(nowPosition);
                        adapter = new QuestAdapter(NormalActivity.this, questItems);
                        listView.setAdapter(adapter);
                        String temp = questItems.size() + "";
                        listCountTv.setText(temp);
                        if(questItems.size() == 0) {
                            listCountTv.setVisibility(View.GONE);
                        }
                        else {
                            listCountTv.setVisibility(View.VISIBLE);
                        }
                    }
                    break;
                }
            }
        }
    }

    // 저장된 값 가져오기 - 각 전시관 QR코드 체크 여부, 사진 체크여부, 튜토리얼 여부, 선택된 랜덤 사진들
    public void loadInfo() {
        for(int i = 0; i < 6; i++) {
            isCheckQrArr[i] = infoData.getBoolean("IS_CHECK_QR_" + i, false);     //전시관 QR코드 체크 여부
            for(int j = 0 ; j < ImgNumByExhibit ; j++) {
                isCheckImgArr[i][j] = infoData.getBoolean("IS_CHECK_PIC_" + (i + 1) + "-" + (j + 1), false); //전시관 이미지 체크 여부
                exhibitName[i][j] = infoData.getString("EXHIBIT_" + (i + 1) + "_" + (j + 1) + "_1", "");

                File file = new File(getCacheDir().toString());
                File[] files = file.listFiles();
                String FileName = infoData.getString("EXHIBIT_" + (i + 1) + "_" + (j + 1) + "_3", "")  + ".jpg";
                Log.d("FileName", FileName);
                for(File tempFile : files) {
                    if(tempFile.getName().contains(FileName)) {
                        Log.d("exhibitdate", tempFile.getName());
                        exhibitImg[i][j] = BitmapFactory.decodeFile(getCacheDir() + "/" + tempFile.getName());
                        break;
                    }
                }
            }
        }
        isShowTutorial = infoData.getBoolean("IS_SHOW_TUTORIAL", false);        //튜토리얼 여부
    }

    public void getExhibitionData() {
        DbConnect dbConnect1 = new DbConnect(this);
        try {
            String result = dbConnect1.execute(DbConnect.GET_EXHIBITION).get();
            Log.d("GET_EXHIBITION", result);
            if(!result.equals("-1")) {
                JSONObject jResult = new JSONObject(result);
                JSONArray jArray = jResult.getJSONArray("result");
                for (int i = 0; i < jArray.length(); i++) {
                    JSONObject jObject = jArray.getJSONObject(i);
                    exhibitionState[i] = jObject.getString("isOpen");
                }
            }
            else {
                Toast.makeText(getApplicationContext(), "네트워크 통신 오류", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "네트워크 통신 오류", Toast.LENGTH_SHORT).show();
        }

        DbConnect dbConnect2 = new DbConnect(this);
        try {
            String result = dbConnect2.execute(DbConnect.GET_QR).get();
            Log.d("GET_QR", result);
            if(!result.equals("-1")) {
                JSONObject jResult = new JSONObject(result);
                JSONArray jArray = jResult.getJSONArray("result");
                for (int i = 0; i < jArray.length(); i++) {
                    JSONObject jObject = jArray.getJSONObject(i);
                    exhibitionQrCode[i] = jObject.getString("address");
                }
            }
            else {
                Toast.makeText(getApplicationContext(), "네트워크 통신 오류", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "네트워크 통신 오류", Toast.LENGTH_SHORT).show();
        }
    }

    /** Service 로 부터 message를 받음 */
    public void onMessage() {
        if (!isService) {
            Log.d("Noti","서비스가 실행 중이 아닙니다. ");
            return;
        }
        //서비스에서 가져온 데이터
        longitude = ms.getLongitude();
        latitude = ms.gatLatitude();
        checkInLocation = ms.checkInLocation();
        setGpsTracking();
    }

    public void setGpsTracking() {
        if (ToggleGps) {
            mapView.removePOIItem(markerGps);
            MapPoint mapPoint = MapPoint.mapPointWithGeoCoord(latitude, longitude);
            markerGps.setItemName("");
            markerGps.setShowCalloutBalloonOnTouch(false);
            markerGps.setMapPoint(mapPoint);
            markerGps.setTag(0);
            markerGps.setMarkerType(MapPOIItem.MarkerType.CustomImage);
            markerGps.setCustomImageBitmap(gps_tracking);
            mapView.addPOIItem(markerGps);
        }
    }

    public void setIn_exhibition() {
        mapView.removeAllPOIItems();

        List<MapPoint> mapPointArr = new ArrayList<>();
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783353, 127.221629));  //제 1전시관
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783802, 127.220985));  //제 2전시관
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.784352, 127.220894));  //제 3전시관
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.784868, 127.220980));  //제 4전시관
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.785106, 127.221696));  //제 5전시관
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.784863, 127.222632));  //제 6전시관
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783092, 127.223064));  //홍보관
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.784983, 127.223346));  //입체상영관
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.784608, 127.223823));  //특별기획전시관

        MapPOIItem[] markerArr = new MapPOIItem[mapPointArr.size()];
        for (int i = 0; i < mapPointArr.size(); i++) {
            markerArr[i] = new MapPOIItem();
            markerArr[i].setMapPoint(mapPointArr.get(i));
            markerArr[i].setTag(i);
            markerArr[i].setItemName("");
            markerArr[i].setShowCalloutBalloonOnTouch(false);
            markerArr[i].setMarkerType(MapPOIItem.MarkerType.CustomImage);

            if (i < 6) {
                if (!(exhibitionState[i].equals("1") || exhibitionState[i].equals("0"))) { //exibitionState에 값 잘못들어가 있을 때
                    markerArr[i].setCustomImageBitmap(in_exhibition_marker[2]);
                }
                if (exhibitionState[i].equals("1") && !isCheckQrArr[i]) {
                    markerArr[i].setCustomImageBitmap(in_exhibition_marker[0]);
                } else if (exhibitionState[i].equals("1") && isCheckQrArr[i]) {
                    markerArr[i].setCustomImageBitmap(in_exhibition_marker[1]);
                } else if (exhibitionState[i].equals("0")) {
                    markerArr[i].setCustomImageBitmap(in_exhibition_marker[2]);
                }
            } else {
                if(i == 7) {
                    markerArr[i].setCustomImageBitmap(in_exhibition_marker[4]);
                }
                else {
                    markerArr[i].setCustomImageBitmap(in_exhibition_marker[3]);
                }
            }
            mapView.addPOIItem(markerArr[i]);
        }
        setGpsTracking();
    }

    public void setOut_exhibition() {
        mapView.removeAllPOIItems();

        List<MapPoint> mapPointArr = new ArrayList<>();
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.780575, 127.227633));  //겨례의 탑
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.782636, 127.224867));  //태극기한마당
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783721, 127.223193));  //겨례의 집
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.785332, 127.219999));  //105인 층계
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.786663, 127.218276));  //추모의 자리
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.780123, 127.222143));  //조선총독부 철거부재 전시공원
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.782238, 127.229518));  //통일염원의 동산
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783914, 127.224861));  //C-47수송기 전시장
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.782167, 127.226047));  //광개토대왕릉비
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.778860, 127.222339));  //밀레니엄 숲
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.781928, 127.226287));  //백련못
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.785811, 127.217168));  //단풍나무 숲길

        MapPOIItem[] markerArr = new MapPOIItem[mapPointArr.size()];
        for (int i = 0; i < mapPointArr.size(); i++) {
            markerArr[i] = new MapPOIItem();

            markerArr[i].setMapPoint(mapPointArr.get(i));
            markerArr[i].setTag(i);

            markerArr[i].setItemName("open_marker");
            markerArr[i].setMarkerType(MapPOIItem.MarkerType.CustomImage);
            markerArr[i].setCustomImageBitmap(out_exhibition_marker[i]);

            markerArr[i].setShowCalloutBalloonOnTouch(false);
            mapView.addPOIItem(markerArr[i]);
        }
        setGpsTracking();
    }

    public void setFacilities() {
        mapView.removeAllPOIItems();

        List<MapPoint> mapPointArr = new ArrayList<>();
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.779731, 127.228604));  //태극열차
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783088, 127.223259));  //겨례쉼터
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783011, 127.223018));  //의무실
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783906, 127.223203));  //음수시설1
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783313, 127.222573));  //음수시설2
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.784530, 127.221057));  //음수시설3
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.785084, 127.222044));  //음수시설4
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783990, 127.222085));  //유아놀이방
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.784198, 127.222242));  //수유실1
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.779536, 127.230317));  //수유실2
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.779347, 127.230344));  //종합안내센터
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783088, 127.222836));  //고객지원센터
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.778109, 127.231310));  //주차장

        MapPOIItem[] markerArr = new MapPOIItem[mapPointArr.size()];
        for (int i = 0; i < mapPointArr.size(); i++) {
            markerArr[i] = new MapPOIItem();
            markerArr[i].setMapPoint(mapPointArr.get(i));
            markerArr[i].setTag(i);
            markerArr[i].setItemName("");
            markerArr[i].setShowCalloutBalloonOnTouch(false);

            markerArr[i].setMarkerType(MapPOIItem.MarkerType.CustomImage);
            markerArr[i].setCustomImageBitmap(facilities_market[i]);

            mapView.addPOIItem(markerArr[i]);
        }
        setGpsTracking();
    }

    public void setStore() {
        mapView.removeAllPOIItems();

        List<MapPoint> mapPointArr = new ArrayList<>();
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.779057, 127.229849));
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.779917, 127.229098));
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.781910, 127.225719));
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.782821, 127.223723));
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783345, 127.223025));
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783912, 127.222242));
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.783766, 127.221051));
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.784162, 127.220880));
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.784626, 127.221008));
        mapPointArr.add(MapPoint.mapPointWithGeoCoord(36.784918, 127.221341));

        MapPOIItem[] markerArr = new MapPOIItem[mapPointArr.size()];
        for (int i = 0; i < mapPointArr.size(); i++) {
            markerArr[i] = new MapPOIItem();
            markerArr[i].setItemName("");
            markerArr[i].setShowCalloutBalloonOnTouch(false);
            markerArr[i].setMapPoint(mapPointArr.get(i));
            markerArr[i].setTag(i);

            markerArr[i].setItemName("open_marker");
            markerArr[i].setMarkerType(MapPOIItem.MarkerType.CustomImage);
            markerArr[i].setCustomImageBitmap(store[0]);

            markerArr[i].setShowCalloutBalloonOnTouch(false);
            mapView.addPOIItem(markerArr[i]);
        }
        setGpsTracking();
    }

    public void timeOn(View view) {
        Intent intent = new Intent(NormalActivity.this, PopupTimeActivity.class);
        intent.putExtra("Time", startDate);
        startActivity(intent);
        overridePendingTransition(R.anim.anim_slide_in_top, R.anim.anim_slide_out_top);
    }

    public void onIn_exhibition(View view) {
        Mode = 0;
        setTogglebut(Mode);
        mapView.setMapCenterPointAndZoomLevel(MapPoint.mapPointWithGeoCoord(36.784116, 127.222147), 1, true);
        setIn_exhibition();
    }

    public void onOut_exhibition(View view) {
        Mode = 1;
        setTogglebut(Mode);
        mapView.setMapCenterPointAndZoomLevel(MapPoint.mapPointWithGeoCoord(36.780575, 127.227633), 1, true);
        setOut_exhibition();
    }

    public void onFacilities(View view) {
        Mode = 2;
        setTogglebut(Mode);
        mapView.setMapCenterPointAndZoomLevel(MapPoint.mapPointWithGeoCoord(36.778816, 127.230159), 1, true);
        setFacilities();
    }

    public void onStore(View view) {
        Mode = 3;
        setTogglebut(Mode);
        mapView.setMapCenterPointAndZoomLevel(MapPoint.mapPointWithGeoCoord(36.778816, 127.230159), 1, true);
        setStore();
    }

    public void onList(View view) {
        Mode = 4;
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        setTogglebut(Mode);
    }

    public void onCheck(View view) {
        Intent intent = new Intent(NormalActivity.this, CheckActivity.class);
        startActivity(intent);
        finish();
    }

    public void onTutorial(View v) {
        Intent intent = new Intent(NormalActivity.this, TutorialActivity.class);
        startActivityForResult(intent, 3000);
    }

    public void onLocation(View view) {
        if(latitude != 0 && longitude != 0) {
            ToggleGps = !ToggleGps;
            if (ToggleGps) {
                mapView.setMapCenterPointAndZoomLevel(MapPoint.mapPointWithGeoCoord(latitude, longitude), zoomLevel, true);
                findViewById(R.id.but_gps).setBackgroundResource(R.drawable.gps_on);
            } else {
                mapView.removePOIItem(markerGps);
                findViewById(R.id.but_gps).setBackgroundResource(R.drawable.gps_off);
            }
        }
    }

    public void onBack(View v) {
        if (v == findViewById(R.id.bt_back)) {
            finish();
        }
    }

    public void setTogglebut(int Mode) {
        mapViewContainer.setVisibility(View.GONE);
        questView.setVisibility(View.GONE);
        but_gps.setVisibility(View.GONE);

        ImageButton[] imgBut = new ImageButton[5];
        imgBut[0] = findViewById(R.id.in_exhibition);
        imgBut[1] = findViewById(R.id.out_exhibition);
        imgBut[2] = findViewById(R.id.facilities);
        imgBut[3] = findViewById(R.id.store);
        imgBut[4] = findViewById(R.id.list);
        TextView[] tv = new TextView[5];
        tv[0] = findViewById(R.id.tv_in_exhibition);
        tv[1] = findViewById(R.id.tv_out_exhibition);
        tv[2] = findViewById(R.id.tv_facilities);
        tv[3] = findViewById(R.id.tv_store);
        tv[4] = findViewById(R.id.tv_list);
        for (int i = 0; i < 5; i++) {
            if (i == Mode) {
                tv[i].setTextColor(Color.parseColor("#3F51B5"));
            } else {
                tv[i].setTextColor(Color.parseColor("#000000"));
            }
        }

        switch (Mode) {
            case 0: {
                imgBut[0].setBackgroundResource(R.drawable.in_exhibition_on);
                imgBut[1].setBackgroundResource(R.drawable.out_exhibition_off);
                imgBut[2].setBackgroundResource(R.drawable.facilities_off);
                imgBut[3].setBackgroundResource(R.drawable.store_off);
                imgBut[4].setBackgroundResource(R.drawable.list_off);
                mapViewContainer.setVisibility(View.VISIBLE);
                but_gps.setVisibility(View.VISIBLE);
                break;
            }
            case 1: {
                imgBut[0].setBackgroundResource(R.drawable.in_exhibition_off);
                imgBut[1].setBackgroundResource(R.drawable.out_exhibition_on);
                imgBut[2].setBackgroundResource(R.drawable.facilities_off);
                imgBut[3].setBackgroundResource(R.drawable.store_off);
                imgBut[4].setBackgroundResource(R.drawable.list_off);
                mapViewContainer.setVisibility(View.VISIBLE);
                but_gps.setVisibility(View.VISIBLE);
                break;
            }
            case 2: {
                imgBut[0].setBackgroundResource(R.drawable.in_exhibition_off);
                imgBut[1].setBackgroundResource(R.drawable.out_exhibition_off);
                imgBut[2].setBackgroundResource(R.drawable.facilities_on);
                imgBut[3].setBackgroundResource(R.drawable.store_off);
                imgBut[4].setBackgroundResource(R.drawable.list_off);
                mapViewContainer.setVisibility(View.VISIBLE);
                but_gps.setVisibility(View.VISIBLE);
                break;
            }
            case 3: {
                imgBut[0].setBackgroundResource(R.drawable.in_exhibition_off);
                imgBut[1].setBackgroundResource(R.drawable.out_exhibition_off);
                imgBut[2].setBackgroundResource(R.drawable.facilities_off);
                imgBut[3].setBackgroundResource(R.drawable.store_on);
                imgBut[4].setBackgroundResource(R.drawable.list_off);
                mapViewContainer.setVisibility(View.VISIBLE);
                but_gps.setVisibility(View.VISIBLE);
                break;
            }
            case 4: {
                imgBut[0].setBackgroundResource(R.drawable.in_exhibition_off);
                imgBut[1].setBackgroundResource(R.drawable.out_exhibition_off);
                imgBut[2].setBackgroundResource(R.drawable.facilities_off);
                imgBut[3].setBackgroundResource(R.drawable.store_off);
                imgBut[4].setBackgroundResource(R.drawable.list_on);
                questView.setVisibility(View.VISIBLE);
                break;
            }
        }
    }

    @Override
    public void onPOIItemSelected(MapView mapView, MapPOIItem mapPOIItem) {  //마커 클릭 핸들링
        int tagNum = mapPOIItem.getTag();
        Intent intent = new Intent(NormalActivity.this, PopupMapActivity.class);

        intent.putExtra("Mode", Mode);
        intent.putExtra("TagNum", tagNum);
        startActivityForResult(intent, 2000);
    }

    @Override
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem) {

    }

    @Override
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem, MapPOIItem.CalloutBalloonButtonType calloutBalloonButtonType) {

    }

    @Override
    public void onDraggablePOIItemMoved(MapView mapView, MapPOIItem mapPOIItem, MapPoint mapPoint) {

    }

    @Override
    public void onCurrentLocationUpdate(MapView mapView, MapPoint mapPoint, float v) {

    }

    @Override
    public void onCurrentLocationDeviceHeadingUpdate(MapView mapView, float v) {

    }

    @Override
    public void onCurrentLocationUpdateFailed(MapView mapView) {

    }

    @Override
    public void onCurrentLocationUpdateCancelled(MapView mapView) {

    }

    @Override
    public void onMapViewInitialized(MapView mapView) {

    }

    @Override
    public void onMapViewCenterPointMoved(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewZoomLevelChanged(MapView mapView, int zoom) {
        zoomLevel = zoom;
    }

    @Override
    public void onMapViewSingleTapped(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDoubleTapped(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewLongPressed(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragStarted(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragEnded(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewMoveFinished(MapView mapView, MapPoint mapPoint) {

    }

    public void initBitmap() {
        gps_tracking = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.gps_tracking);
        gps_tracking = Bitmap.createScaledBitmap(gps_tracking, 100, 100, true);

        in_exhibition_marker[0] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.in_exhibition_marker00);
        in_exhibition_marker[1] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.in_exhibition_marker01);
        in_exhibition_marker[2] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.in_exhibition_marker02);
        in_exhibition_marker[3] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.in_exhibition_marker03);
        in_exhibition_marker[4] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.in_exhibition_marker04);
        for (int i = 0; i < in_exhibition_marker.length; i++) {
            in_exhibition_marker[i] = Bitmap.createScaledBitmap(in_exhibition_marker[i], 110, 110, true);
        }

        out_exhibition_marker[0] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker00);
        out_exhibition_marker[1] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker01);
        out_exhibition_marker[2] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker02);
        out_exhibition_marker[3] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker03);
        out_exhibition_marker[4] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker02);
        out_exhibition_marker[5] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker04);
        out_exhibition_marker[6] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker04);
        out_exhibition_marker[7] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker05);
        out_exhibition_marker[8] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker02);
        out_exhibition_marker[9] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker04);
        out_exhibition_marker[10] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker04);
        out_exhibition_marker[11] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.out_exhibition_marker04);
        for (int i = 0; i < out_exhibition_marker.length; i++) {
            out_exhibition_marker[i] = Bitmap.createScaledBitmap(out_exhibition_marker[i], 90, 90, true);
        }

        facilities_market[0] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker00);
        facilities_market[1] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker01);
        facilities_market[2] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker02);
        facilities_market[3] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker03);
        facilities_market[4] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker03);
        facilities_market[5] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker03);
        facilities_market[6] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker03);
        facilities_market[7] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker04);
        facilities_market[8] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker05);
        facilities_market[9] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker05);
        facilities_market[10] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker06);
        facilities_market[11] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker06);
        facilities_market[12] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.facilities_marker07);
        for (int i = 0; i < facilities_market.length; i++) {
            facilities_market[i] = Bitmap.createScaledBitmap(facilities_market[i], 90, 90, true);
        }

        store[0] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.store_marker03);
        store[1] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.store_marker00);
        store[2] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.store_marker00);
        store[3] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.store_marker00);
        store[4] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.store_marker01);
        store[5] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.store_marker03);
        store[6] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.store_marker00);
        store[7] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.store_marker00);
        store[8] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.store_marker03);
        store[9] = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.store_marker00);
        for (int i = 0; i < store.length; i++) {
            store[i] = Bitmap.createScaledBitmap(store[i], 90, 90, true);
        }

    }


    public void QuestItemSet() {
        for (questNum = 0 ; questNum < qTitle.size() ; questNum++) {
            questItems.add(new questViewItems() {{
                QImageItem = qImages.get(questNum);
                QTitleItem = qTitle.get(questNum);
                QSubTitleItem = qDescription.get(questNum);
                QExhibitionNum = qExhibitionNum.get(questNum);
                QTypeItem = qType.get(questNum);
                QNumOfImg = qNumOfImg.get(questNum);
            }});
        }
    }
    public void QuestItemChange(int exhibitionNumber, int exhibitNumber) {
        for (int i = 0; i < questItems.size(); i++) {
            if (questItems.get(i).QExhibitionNum == exhibitionNumber && questItems.get(i).QNumOfImg == exhibitNumber) {
                questItems.get(i).QImageItem = exhibitImg[exhibitionNumber][exhibitNumber];     //찍을 이미지로 바꿔줘야함**
            }
        }
    }

    //QuestView의 리스트들 클릭됬을 때
    public void QuestviewClick() {
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if(checkInLocation) {
                        int type = questItems.get(position).QTypeItem;
                        if (type == 0) { //QR코드인 경우
                            IntentIntegrator qrScan = new IntentIntegrator(NormalActivity.this);
                            qrScan.setCaptureActivity(QrActivity.class);
                            qrScan.setBeepEnabled(false);
                            qrScan.setPrompt("전시관의 QR코드를 스캔해주세요.");
                            qrScan.setCameraId(0);
                            qrScan.setOrientationLocked(false);
                            qrScan.initiateScan();
                        } else if (type == 1) {    //사진인 경우
                            nowPosition = position;
                            Intent intent = new Intent(NormalActivity.this, ShowImageActivity.class);
                            intent.putExtra("exhibitionNum", questItems.get(position).QExhibitionNum);      //전시관 번호(0~5)
                            intent.putExtra("imgNum", questItems.get(position).QNumOfImg);                  //몇번째 이미지인지(0~max-1)
                            startActivityForResult(intent, 1000);
                        }
                    }
                    else {
                        Toast.makeText(getApplicationContext(), "독립기념관 외부로 나가면 기록이 되지 않습니다.!", Toast.LENGTH_SHORT).show();
                    }
                }
            });
    }

    class QuestAdapter extends BaseAdapter {
        private final List<questViewItems> items;
        private final LayoutInflater qinflator;

        QuestAdapter(Activity context, List<questViewItems> items) {
            super();

            this.items = items;
            this.qinflator = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        //list test
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            questViewItems qItem = items.get(position);

            View qcontents = convertView;

            if (convertView == null)
                qcontents = qinflator.inflate(R.layout.list_quest, null);

            ImageView images = qcontents.findViewById(R.id.questImage);
            TextView myTItle = qcontents.findViewById(R.id.QtextMain);
            TextView myDescription = qcontents.findViewById(R.id.QtextSub);

            images.setImageBitmap(qItem.QImageItem);
            myTItle.setText(qItem.QTitleItem);
            myDescription.setText(qItem.QSubTitleItem);

            return qcontents;
        }
    }

    class questViewItems {
        Bitmap QImageItem;
        String QTitleItem;
        String QSubTitleItem;
        int QExhibitionNum;          //전시관 번호
        int QTypeItem;               //Qr인지 사진인지
        int QNumOfImg;               //몇번째 사진인지
    }

    public void attributeSetting() {
        for(int i = 0 ; i < 6 ; i++){
            if(exhibitionState[i].equals("1")) {
                for(int j = 0 ; j < ImgNumByExhibit ; j++) {
                    if(!isCheckImgArr[i][j] && exhibitName[i][j] != "") {
                        qTitle.add(Title[i]);
                        qDescription.add("해당 전시관의 전시물 \"" + exhibitName[i][j] + "\"를 찾아 사진을 찍으세요.");
                        qImages.add(exhibitImg[i][j]);     //찍을 이미지로 바꿔줘야함**
                        qExhibitionNum.add(i);
                        qType.add(1);
                        qNumOfImg.add(j);
                    }
                }

                if (!isCheckQrArr[i]) {
                    qTitle.add(Title[i]);
                    qDescription.add(qrDescription);
                    qImages.add(qrImages);
                    qExhibitionNum.add(i);
                    qType.add(0);
                    qNumOfImg.add(-1);
                }
            }
        }
    }

    static class NormalClass {
        int getImgNumByExhibit() { return ImgNumByExhibit; }
    }
}