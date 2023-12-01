package com.example.picpic;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.BoundingPoly;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import com.google.cloud.vision.v1.Vertex;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMAGE_REQUEST= 1;// 이미지 선택을 위한 요청 코드
    private ImageView imageView; // 이미지를 표시할 ImageView
    private Bitmap originalBitmap;// 선택된 원본 이미지
    private BatchAnnotateImagesResponse responses; // Cloud Vision API 응답을 저장
    private List<JsonArray> firstList = new ArrayList<>(); // 좌표를 저장할 리스트 (단어)
    private List<String> secondList = new ArrayList<>(); // 텍스트를 저장할 리스트 (열)
    private List<String> thirdList = new ArrayList<>(); // 텍스트를 저장할 리스트 (단어)

    private List<String>detectList = new ArrayList<>();
    private List<String>test1 = new ArrayList<>();
    private List<String>test2 = new ArrayList<>();

    private List<Integer> matchingIndices = new ArrayList<>(); // 몇번쨰 열에서 검출되었는지 ( second)

    private List<Integer> resultIndices = new ArrayList<>();//  검출된 단어 목록 (검출된 열의 모든 단어 )



    //단어를 열에 맞춰 인덱싱할때 쓰이는 문자 길이
    private List<Integer> secondListLength = new ArrayList<>();
    private List<Integer> thirdListLength = new ArrayList<>();


    private List<Integer> indexL = new ArrayList<>(); //단어마다 어떤 열에 해당하는지에 대한 인덱스

    private List<String> regexPatterns; //정규표현식



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //위젯 연결
        imageView = findViewById(R.id.imageView);//사진 출력할 imageview
        TextView tv = findViewById(R.id.textView);//검출된 개인정보 개수 출력 textview




        // Cloud Vision SDK 초기화
        try {
            Class.forName("com.google.cloud.vision.v1.ImageAnnotatorClient");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }



        //버튼과 메소드 연결=============================================================================

        //사진 선택
        findViewById(R.id.btnSelectImage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectImage();
            }
        });
        //개인정보 검사
        findViewById(R.id.btnTestImage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testImage();
            }
        });
        //검출된 개인정보 목록 보기
        findViewById(R.id.btnList).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                checkRegex();
//                showDetectedInfoDialog();
                showList();
            }
        });



        //마스킹 된 사진 저장하기
        findViewById(R.id.btnSaveImage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bitmap blurredBitmap = getBitmapFromImageView(); // 또는 블러 처리된 이미지를 가져오는 다른 방법 사용
                if (blurredBitmap != null) {
                    saveImageToGallery(blurredBitmap);
                } else {
                    Toast.makeText(MainActivity.this, "No blurred image to save", Toast.LENGTH_SHORT).show();
                }
            }
        });


        //정규표현식 초기 설정
        initializeRegexPatterns();
    }








    //사진 선택=====================================================================
    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent,PICK_IMAGE_REQUEST);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode ==PICK_IMAGE_REQUEST&& resultCode ==RESULT_OK&& data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            displaySelectedImage(imageUri);
        }
    }
    //사진 화면에 출력
    private void displaySelectedImage(Uri imageUri) {
        try {
            InputStream imageStream = getContentResolver().openInputStream(imageUri);
            Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
            imageView.setImageBitmap(selectedImage);
            originalBitmap = selectedImage.copy(selectedImage.getConfig(), true); // 원본 이미지 복사
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    // 이미지 내 개인정보 검사===============================================================
    private void testImage() {

        if (imageView.getDrawable() == null) {
            showErrorDialog("Error", "Please select an image first");
            return;
        }

        Log.d("MainActivity", "Testing image...");

        try {
            Bitmap bitmap = getBitmapFromImageView();
            if (bitmap != null) {
                Log.d("MainActivity", "Bitmap obtained");
                detectTextFromImage(bitmap); // 텍스트 탐지 수행

                // 텍스트 탐지 후, 빨간색 사각형으로 표시
                if (!firstList.isEmpty()) {
                    for (int i = 0; i < firstList.size(); i++) {
                        drawBoundingBoxOnImage(firstList.get(i), i); // 사각형 그리기
                    }
                }
            } else {
                showErrorDialog("Error", "Failed to get bitmap from ImageView");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Error", e.getMessage());
        }
    }




    // Cloud Vision API를 사용하여 이미지에서 텍스트 탐지
    private void detectTextFromImage(Bitmap bitmap) {
        Log.d("MainActivity", "Detecting text from image...");

        try {
            // 리소스 ID 얻기
            int resId = getResources().getIdentifier("credentials", "raw", getPackageName());

            // InputStream으로 파일 읽기
            InputStream credentialsStream = getResources().openRawResource(resId);

            // API 키를 설정하여 ImageAnnotatorClient를 생성
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(credentialsStream);
            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();
            ImageAnnotatorClient vision = ImageAnnotatorClient.create(settings);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] imageBytes = stream.toByteArray();
            ByteString byteString = ByteString.copyFrom(imageBytes);

            Image image = Image.newBuilder().setContent(byteString).build();
            Feature feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
            AnnotateImageRequest request =
                    AnnotateImageRequest.newBuilder().addFeatures(feature).setImage(image).build();

            // responses 변수를 멤버 변수로 선언
            responses = vision.batchAnnotateImages(Collections.singletonList(request));

            StringBuilder resultText = new StringBuilder();

            for (AnnotateImageResponse res : responses.getResponsesList()) {
                if (res.hasError()) {
                    String errorMessage = res.getError().getMessage();
                    Log.e("Vision API Error", errorMessage);
                    showErrorDialog("Error", errorMessage);
                    return;
                }

                // 문장 단위로 좌표 추출
                resultText.append(extractSentenceCoordinates(res)).append("\n");
            }

            // 리스트 비우기
            matchingIndices.clear();
            resultIndices.clear();

            // 정규 표현식에 부합하는 텍스트의 좌표를 가져옴
            matchingIndices = getIndicesMatchingRegexPatterns(secondList, regexPatterns);
            resultIndices = getMatchingIndices(matchingIndices, indexL);

            //개수 출력
            TextView tv = findViewById(R.id.textView);//검출된 개인정보 개수 출력 textview
            tv.setText("개인정보가 " + matchingIndices.size()+"개 검출되었습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Error", e.getMessage());
        }
    }
    private void drawBoundingBoxOnImage(JsonArray boundingBoxArray, int index) {
        // 이미지 뷰에서 비트맵 가져오기
        Bitmap originalBitmap = getBitmapFromImageView();

        // 비트맵 복제
        Bitmap bitmap = originalBitmap.copy(originalBitmap.getConfig(), true);

        // 비트맵에서 캔버스 생성
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);

        // 좌표를 사용하여 사각형 직선으로 그리기
        if (resultIndices.contains(index)) {
            for (int i = 0; i < boundingBoxArray.size(); i++) {
                JsonObject vertexJson = boundingBoxArray.get(i).getAsJsonObject();
                float x = vertexJson.getAsJsonPrimitive("x").getAsFloat();
                float y = vertexJson.getAsJsonPrimitive("y").getAsFloat();

                // 연결된 다음 좌표 인덱스 계산
                int nextIndex = (i + 1) % boundingBoxArray.size();
                JsonObject nextVertexJson = boundingBoxArray.get(nextIndex).getAsJsonObject();
                float nextX = nextVertexJson.getAsJsonPrimitive("x").getAsFloat();
                float nextY = nextVertexJson.getAsJsonPrimitive("y").getAsFloat();

                // 텍스트 주변에 직선으로 사각형 그리기
                canvas.drawLine(x, y, nextX, nextY, paint);
            }
            // 수정된 비트맵으로 이미지 뷰 업데이트
            imageView.setImageBitmap(bitmap);
        }
    }

    // 문장단위 좌표 추출
    private JsonArray extractSentenceCoordinates(AnnotateImageResponse response) {
        JsonArray sentencesJsonArray = new JsonArray();
        firstList.clear(); // 리스트 초기화
        secondList.clear(); // 리스트 초기화
        thirdList.clear(); // 리스트 초기화
        indexL.clear();//리스트 초기화
        thirdListLength.clear();
        secondListLength.clear();

        for (int i = 0; i < response.getTextAnnotationsList().size(); i++) {
            EntityAnnotation annotation = response.getTextAnnotations(i);
            String text = annotation.getDescription();

            if (i == 0) {
                String[] lines = text.split("\n");
                for (String line : lines) {
                    secondList.add(line);
                }
                // 첫 번째 텍스트를 secondList에 추가
            } else {
                thirdList.add(text); // 나머지 텍스트를 thirdList에 추가
            }

            BoundingPoly boundingPoly = annotation.getBoundingPoly();
            JsonArray verticesJsonArray = new JsonArray();

            if (i != 0) { // i가 0이 아닌 경우에만 실행
                for (Vertex vertex : boundingPoly.getVerticesList()) {
                    JsonObject vertexJson = new JsonObject();
                    vertexJson.addProperty("x", vertex.getX());
                    vertexJson.addProperty("y", vertex.getY());
                    verticesJsonArray.add(vertexJson);
                }
                firstList.add(verticesJsonArray); // 좌표를 firstList에 추가

            }

            JsonObject sentenceJson = new JsonObject();
            sentenceJson.addProperty("text", text);
            sentenceJson.add("boundingBox", verticesJsonArray);
            sentencesJsonArray.add(sentenceJson);
        }
        Length(secondList, secondListLength, "secondList");
        Length(thirdList, thirdListLength, "thirdList");
        calculateIndexAndSum(secondListLength, thirdListLength);
        return sentencesJsonArray;
    }
    private List<Integer> getIndicesMatchingRegexPatterns(List<String> inputList, List<String> regexPatterns) {
        List<Integer> matchingIndices = new ArrayList<>();

        for (int i = 0; i < inputList.size(); i++) {
            String text = inputList.get(i);

            // 정규 표현식 패턴에 대해 루프 수행
            for (String regexPattern                                                                                                                                                                                                         : regexPatterns) {
                // 정규 표현식에 맞는지 확인
                if (Pattern.matches(regexPattern, text)) {
                    matchingIndices.add(i);
                    break;
                }
            }
        }
        return matchingIndices;
    }
    private List<Integer> getMatchingIndices(List<Integer> matchingIndices, List<Integer> indexL) {
        List<Integer> resultIndices = new ArrayList<>();

        for (int i = 0; i < indexL.size(); i++) {
            int indexLValue = indexL.get(i);

            // indexL의 값이 matchingIndices에 포함되어 있는지 확인
            if (matchingIndices.contains(indexLValue)) {
                resultIndices.add(i);
            }
        }

        return resultIndices;

    }
    private void Length(List<String> inputList, List<Integer> targetList, String listName) {
        for (String item : inputList) {
            // 공백을 제거한 후 길이를 계산하여 리스트에 추가
            //int length = item.replace(" ", "").length();
            int length = item.replaceAll("\\s+", "").length();

            targetList.add(length);
        }
    }
    private void calculateIndexAndSum(List<Integer> secondListLength, List<Integer> thirdListLength) {
        int index_car = 0;
        int sum = 0;

        for(int j = 0; j < thirdListLength.size(); j++){
            sum += thirdListLength.get(j);
            indexL.add(index_car);

            if (sum == secondListLength.get(index_car)) {
                index_car++;
                sum = 0;
            }
        }
    }




    //검출된 목록 보기===========================================================

    private void showList() {
        // 검출된 개인정보 목록을 다이얼로그에 체크리스트로 표시하는 코드
        final boolean[] checkedItems = new boolean[matchingIndices.size()]; //블러에 전달한 거
        detectList.clear();
        for (int i : matchingIndices) {
            detectList.add(secondList.get(i));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("검출된 개인정보 목록")
                .setMultiChoiceItems(detectList.toArray(new CharSequence[detectList.size()]), checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checkedItems[which] = isChecked; //블러에 전달할거
                    }
                })
                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 확인 버튼을 눌렀을 때 동작하는 부분
                        applyBlurToSelectedRectangles(checkedItems);

                    }
                })
                .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 취소 버튼을 눌렀을 때 동작하는 부분 (아직 암것두 안함 뭐가있지)
                    }
                })
                .create()
                .show();
    }


//
//    private void showDetectedInfoDialog() {
//        // 검출된 개인정보 목록을 다이얼로그에 체크리스트로 표시하는 코드
//        final List<String> detectedInfoList = getDetectedPersonalInformation();
//        final boolean[] checkedItems = new boolean[detectedInfoList.size()];
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//
//        builder.setTitle("검출된 개인정보 목록")
//                .setMultiChoiceItems(detectList.toArray(new CharSequence[detectList.size()]), checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
//                        checkedItems[which] = isChecked; //
//                    }
//                })
//                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        // 확인 버튼을 눌렀을 때 동작하는 부분
//                        applyBlurToSelectedRectangles(checkedItems);
//
//                    }
//                })
//                .setNegativeButton("취소", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        // 취소 버튼을 눌렀을 때 동작하는 부분 (아직 암것두 안함 뭐가있지)
//                    }
//                })
//                .create()
//                .show();
//    }

    private List<String> getDetectedPersonalInformation() {
        List<String> detectedInfoList = new ArrayList<>();

        // 이전의 코드와 동일하게 검출된 개인정보를 가져와서 리스트에 추가
        for (int index : resultIndices) {
            if (index < thirdList.size()) {
                detectedInfoList.add(thirdList.get(index));
            }
        }

        return detectedInfoList;
    }


    private void applyBlurToSelectedRectangles(boolean[] checkedItems) {
        if (originalBitmap != null) {
            Bitmap bitmapCopy = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(bitmapCopy);

            for (int i = 0; i < checkedItems.length; i++) {
                if (checkedItems[i]) {

                    for(int j = 0; j<indexL.size(); j++){
                        if(indexL.get(j) == matchingIndices.get(i)){

                            test1.add(matchingIndices.get(i).toString());
                            test2.add(indexL.get(j).toString());

                            JsonArray boundingBox = firstList.get(j);
                            Path path = new Path();
                            setPathFromBoundingBox(path, boundingBox);
                            canvas.save();
                            canvas.clipPath(path);
                            Bitmap blurredBitmap = applyBlur(originalBitmap);
                            canvas.drawBitmap(blurredBitmap, 0, 0, null);
                            canvas.restore();
                        }
                }

                }
                TextView tv = findViewById(R.id.textView);//검출된 개인정보 개수 출력 textview
                tv.setText("개인정보가 " + test2.toString()+" ////" +test2.toString() );
            }
            imageView.setImageBitmap(bitmapCopy);
        } else {
            showErrorDialog("Error", "Failed to get original bitmap");
        }
    }

    //마스킹된 사진 저장하기======================================================================
    private void saveImageToGallery(Bitmap bitmap) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "blurred_image_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }




    // ImageView에서 Bitmap을 가져오는 메서드
    private Bitmap getBitmapFromImageView() {
        try {
            Drawable drawable = imageView.getDrawable();
            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }




    // 블러처리================================================================
    private void applyBlurToRedRectangles() {
        if (originalBitmap != null) {
            Bitmap bitmapCopy = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(bitmapCopy);

            // 해당 좌표에 블러 처리 적용
            for (int index : resultIndices) {
                if (index < firstList.size()) {
                    JsonArray boundingBox = firstList.get(index);

                    // 사각형 영역 정의 및 클리핑
                    Path path = new Path();
                    setPathFromBoundingBox(path, boundingBox);
                    canvas.save();
                    canvas.clipPath(path);

                    // 해당 영역에 블러 처리
                    Bitmap blurredBitmap = applyBlur(originalBitmap);
                    canvas.drawBitmap(blurredBitmap, 0, 0, null);

                    // 클리핑 해제
                    canvas.restore();
                }
            }
            imageView.setImageBitmap(bitmapCopy);
        } else {
            showErrorDialog("Error", "Failed to get original bitmap");
        }
    }
    private void setPathFromBoundingBox(Path path, JsonArray boundingBox) {
        int padding = 10; // 확장할 여백 크기

        for (int i = 0; i < boundingBox.size(); i++) {
            JsonObject vertex = boundingBox.get(i).getAsJsonObject();
            float x = vertex.get("x").getAsFloat();
            float y = vertex.get("y").getAsFloat();

            // 첫 번째 점인 경우
            if (i == 0) {
                path.moveTo(x - padding, y - padding);
            } else if (i == 1) {
                path.lineTo(x + padding, y - padding);
            } else if (i == 2) {
                path.lineTo(x + padding, y + padding);
            } else if (i == 3) {
                path.lineTo(x - padding, y + padding);
            }

            // 마지막 점에서 첫 번째 점으로 경로를 닫기
            if (i == boundingBox.size() - 1) {
                JsonObject firstVertex = boundingBox.get(0).getAsJsonObject();
                float firstX = firstVertex.get("x").getAsFloat();
                float firstY = firstVertex.get("y").getAsFloat();
                path.lineTo(firstX - padding, firstY - padding);
            }
        }

        path.close();
    }
    private Bitmap applyBlur(Bitmap image) {
        float radius = 25.0f; // 블러 강도
        Bitmap blurred = image.copy(image.getConfig(), true);

        RenderScript rs = RenderScript.create(this);
        Allocation input = Allocation.createFromBitmap(rs, image, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        Allocation output = Allocation.createTyped(rs, input.getType());

        ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        script.setRadius(radius);
        // 첫 번째 블러 처리
        script.setInput(input);
        script.forEach(output);
        output.copyTo(blurred);
        // 두 번째 블러 처리
        input = Allocation.createFromBitmap(rs, blurred, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        script.setInput(input);
        script.forEach(output);
        output.copyTo(blurred);
        // 세 번째 블러 처리
        input = Allocation.createFromBitmap(rs, blurred, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        script.setInput(input);
        script.forEach(output);
        output.copyTo(blurred);

        rs.destroy();
        return blurred;
    }

    //================================================================================

    //에러 발생시 알림창
    private void showErrorDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .create()
                .show();
    }

    private List<String> initializeRegexPatterns() {

        // 주민등록번호 정규 표현식
        String[] ResidentRegistrationNumberPatterns = {
                "\\d{2}(0[1-9]|1[0-2])(0[1-9]|[1-2]\\d|3[0-1])[-. ][1-4]\\d{3,6}",
                ".(0[1-9]|1[0-2])(0[1-9]|[1-2]\\d|3[0-1])[-. ][1-4]\\d{3,6}",
                "\\d(0[1-9])(0[1-9]|[1-2]\\d|3[0-1])[-. ][1-4]\\d{3,6}",
                "1([0-2])(0[1-9]|[1-2]\\d|3[0-1])[-. ][1-4]\\d{3,6}",
                ".(0[1-9]|[1-2]\\d|3[0-1])[-. ][1-4]\\d{3,6}"
        };

        //계좌번호 정규 표현식
        String[] accountNumberPatterns = {
                ".*"  + "\\b0\\d{3}(01|02|24|05|04|25|26)[ -]*\\d{4}[ -]*\\d{4}" + "\\b.*", // 국민은행
                ".*\\b\\d{3}[ -]*(01|02|24|05|04|25|26)[ -]*\\d{4}[ -]*\\d{3}\\b.*", // 국민은행 (구)
                ".*\\b\\d{6}[ -]*(01|02|25|06|18|37|90)[ -]*\\d{4}\\b.*",// 한국주택은행 (구 12자리)
                ".*\\b\\d{4}[ -]*(01|02|25|06|18|37|90)[ -]*\\d{8}\\b.*",// 한국주택은행 (구 14자리)
                ".*" + "\\b1(006|007|002|004|003|005)[ -]*\\d{3}[ -]*\\d{6}\\b" + ".*", // 우리은행
                ".*" + "\\b(1[0-5][0-9]|16[0-1])[ -]*\\d{2}[ -]*\\d{6}\\b" + ".*", // 신한은행 (구)
                ".*" + "\\b(1[0-5][0-9]|16[0-1])[ -]*\\d{3}[ -]*\\d{6}\\b" + ".*", // 신한은행 (신)
                ".*" + "\\b\\d{3}[ -]*\\d{6}[ -]*\\d{3}(05|07|08|02|01|04|94)\\b" + ".*", // 하나은행
                ".*" + "\\b\\d{3,4}[ -]*\\d(01|02|12|06|05|17)[ -]*\\d{6}\\b" + ".*", // 농협은행 (구 11자리)
                ".*\\b3(01|02|12|06|05|17)[ -]*\\d{4}[ -]*\\d{4}[ -]*\\d{2}\\b.*",// 농협은행 (신 13자리)
                ".*" + "\\b\\d{6}[ -]*\\d(51|52|56)[ -]*\\d{6}\\b" + ".*", // 단위농협 (구 14자리)
                ".*" + "\\b3(51|52|56)[ -]*\\d{4}[ -]*\\d{4}[ -]*\\d{2}\\b" + ".*", // 단위농협 (13자리)
                ".*\\b\\d{3}[ -]*(01|02|03|13|07|06|04)\\d{1}[ -]*\\d{6}\\b.*", // 기업은행 (12자리)
                ".*" + "\\b\\d{3}[ -]*\\d{6}[ -]*(01|02|03|13|07|06|04)[ -]*\\d{3}\\b" + ".*", // 기업은행 (14자리)
                ".*" + "\\b(013|020|019|011|022)[ -]*\\d{4}[ -]*\\d{5}\\b" + ".*", // 산업은행
                ".*" + "\\b(102|202|209|103|208|106|108|113|114|206)[ -]*\\d{4}[ -]*\\d{5}\\b" + ".*", // 수협은행
                ".*" + "\\b(100|106|300|190)[ -]*\\d(0|8)d{3}[ -]*\\d{6}\\b" + ".*", // 토스뱅크
                ".*" + "\\b(3333|3388|3355|3310|7777|7979)[ -]*\\d{2}[ -]*\\d{7}\\b" + ".*", //카카오뱅크
                ".*" + "\\b9(002|003|004|005|072|090|091|092|093|200|202|205|207|208|209|210|212)[ -]*\\d{4}[ -]*\\d{4}[ -]*\\d{1}\\b" + ".*", //새마을금고 (신)
                ".*" + "^d{4}(09|10|13|37|)\\d{7}$"+ ".*",//새마을금고(구 13자리)
                ".*" + "^\\d{4}8(0156)\\d{1}\\d{7}$"+ ".*" //새마을 금고(구14자리)
        };

        // 전화번호 정규 표현식
        String regexPattern3 = ".*" + "\\b(?:010|02|031|032|033|041|042|043|044|051|052|053|054|055|061|062|063|064)[ -]*\\d{2,4}[ -]*\\d{2,4}\\b" + ".*";
        String regexPattern4 = ".*" + "\\b(?:0[1-9]\\d{0,2}|1[0-6]\\d{0,2})\\d{4}\\d{4}\\b" + ".*";
        String regexPattern5 = ".*" + "\\b(?:0[1-9]\\d{0,2}|1[0-6]\\d{0,2})[. -]*\\d{1,4}[. -]*\\d{4}\\b" + ".*";

        // 카드번호 정규 표현식
        String[] creditCardPatterns = {
                "\\b4\\d{3}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b",         // VISA
                "\\b5[1-5]\\d{2}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b",     // MasterCard
                "\\b(?:\\d{4}[ -]?\\d{6}[ -]?\\d{5}|37\\d{2}[ -]?\\d{6}[ -]?\\d{5})\\b",   // American Express
                "\\b(?:62|\\d{4}[ -]?){3}\\d{4,11}\\b",                    // China UnionPay
                "\\b9\\d{3}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b",          // 국내 카드 회사
                "\\b(?:2131|1800|35\\d{3})[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b",  // JCB
                "\\b3(?:0[0-5]|[68][0-9])\\d{11}\\b",                      // Diners Club International
                "\\b6(?:011|5[0-9]{2})[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b",   // Discover Card
                "\\b(?:^|\\b)(?:\\d{0,3} ?)?\\d{4} ?\\d{4} ?\\d{4}(?:\\b|$)",    // Visa 추가
                "\\b(?:\\s*\\d{0,3}\\s*)?\\d{1,5}\\s*\\d{4}\\s*\\d{4}\\s*\\d{4}\\b",  // MasterCard 추가
                "\\b(?:\\s*\\d{0,4}\\s*)?(?:62|\\d{4}\\s*){2,3}\\d{4,11}\\b",     // UnionPay 추가
                "\\b(?:\\s*\\d{0,4}\\s*)?(?:9\\d{3}|\\d{1,8})\\s*\\d{4}\\s*\\d{4}\\s*\\d{4}\\b",  // 국내 카드 회사 추가
                "\\b(?:35\\d{2}(?:\\s*\\d{4}){3}|\\s*(?:2[13]|1800)?\\s*\\d{0,4}(?:\\s*\\d{4}){2}\\s*\\d{4})\\b",  // JCB 추가
                "\\b(?:\\s*\\d{0,4}\\s*)?(?:6(?:011|5[0-9]{2})|\\d{1,8})\\s*\\d{4}\\s*\\d{4}\\s*\\d{4}\\b"   // Discover 추가
        };

        // 정규 표현식 패턴 리스트 초기화
        regexPatterns = new ArrayList<>();
        regexPatterns.addAll(Arrays.asList(ResidentRegistrationNumberPatterns));
        regexPatterns.addAll(Arrays.asList(accountNumberPatterns));
        regexPatterns.add(regexPattern3);
        regexPatterns.add(regexPattern4);
        regexPatterns.add(regexPattern5);
        regexPatterns.addAll(Arrays.asList(creditCardPatterns));

        return regexPatterns;
    }

//
//    private void checkRegex() {
//        if (secondList.isEmpty()) {
//            showErrorDialog("Error", "No text detected. Please analyze the image first.");
//            return;
//        }
//
//        // Initialize regex patterns
//        initializeRegexPatterns();
//
//        // StringBuilder to store matched values
//        StringBuilder matchedValuesBuilder = new StringBuilder();
//
//        for (int i = 0; i < secondList.size(); i++) {
//            String text = secondList.get(i).trim(); // Trim to remove leading/trailing whitespaces
//            boolean isMatched = false;
//            String matchedPattern = "";
//
//            for (String regexPattern : regexPatterns) {
//                // Check if the text matches the regex pattern
//                if (Pattern.matches(regexPattern, text)) {
//                    isMatched = true;
//                    matchedPattern = regexPattern;
//                    break;
//                }
//            }
//
//            // Log the matched pattern and text for debugging
//            Log.d("Regex", "Text: " + text + ", Matched Pattern: " + matchedPattern);
//
//            // If matched, append the result to the StringBuilder
//            if (isMatched) {
////                matchedValuesBuilder.append("Text: ").append(text).append("\nPattern: ").append(matchedPattern).append("\n\n");
//            }
//        }
//
//        // Display the matched values in an AlertDialog
//        if (matchedValuesBuilder.length() > 0) {
////            showAlert("Matched Values", matchedValuesBuilder.toString());
//        } else {
////            showAlert("No Matches", "No text matched any of the regex patterns.");
//        }
//
//        for (int i = 0; i < secondList.size(); i++) {
//            String text = secondList.get(i).trim(); // Trim to remove leading/trailing whitespaces
//            boolean isMatched = false;
//            String matchedPattern = "";
//
//            for (String regexPattern : regexPatterns) {
//                // Check if the text matches the regex pattern
//                if (Pattern.matches(regexPattern, text)) {
//                    isMatched = true;
//                    matchedPattern = regexPattern;
//                    break;
//                }
//            }
//
//            // Log the matched pattern and text for debugging
//            if (isMatched) {
//                Log.d("Regex", "Text: " + text + ", Matched Pattern: " + matchedPattern);
//                matchedValuesBuilder.append("Text: ").append(text).append("\nPattern: ").append(matchedPattern).append("\n\n");
//            } else {
//                Log.d("Regex", "No match for text: " + text);
//            }
//        }
//
//    }
//
//    private void showAlert(String title, String message) {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle(title)
//                .setMessage(message)
//                .setPositiveButton("OK", null)
//                .create()
//                .show();
//    }



}
