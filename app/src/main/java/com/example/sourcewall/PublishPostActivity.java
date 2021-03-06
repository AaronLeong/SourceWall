package com.example.sourcewall;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;

import com.example.sourcewall.connection.ResultObject;
import com.example.sourcewall.connection.api.APIBase;
import com.example.sourcewall.connection.api.PostAPI;
import com.example.sourcewall.connection.api.QuestionAPI;
import com.example.sourcewall.dialogs.InputDialog;
import com.example.sourcewall.model.PrepareData;
import com.example.sourcewall.model.SubItem;
import com.example.sourcewall.util.Consts;
import com.example.sourcewall.util.FileUtil;
import com.example.sourcewall.util.SketchSharedUtil;
import com.example.sourcewall.util.ToastUtil;

import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.util.ArrayList;


/**
 *
 */
public class PublishPostActivity extends SwipeActivity implements View.OnClickListener {
    private EditText titleEditText;
    private EditText tagEditText;
    private EditText bodyEditText;
    private ImageButton imgButton;
    private ImageButton insertButton;
    private Spinner spinner;
    private View uploadingProgress;
    private ProgressDialog progressDialog;
    private String tmpImagePath;
    private SubItem subItem;
    private String group_id = "";
    private String csrf = "";
    private String topic = "";
    private ArrayList<BasicNameValuePair> topics = new ArrayList<>();
    private PrepareTask prepareTask;
    private boolean replyOK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publish_post);
        Toolbar toolbar = (Toolbar) findViewById(R.id.action_bar);
        setSupportActionBar(toolbar);
        titleEditText = (EditText) findViewById(R.id.text_post_title);
        tagEditText = (EditText) findViewById(R.id.text_question_tag);
        bodyEditText = (EditText) findViewById(R.id.text_post_body);
        spinner = (Spinner) findViewById(R.id.spinner_post_topic);
        ImageButton publishButton = (ImageButton) findViewById(R.id.btn_publish);
        imgButton = (ImageButton) findViewById(R.id.btn_add_img);
        insertButton = (ImageButton) findViewById(R.id.btn_insert_img);
        ImageButton linkButton = (ImageButton) findViewById(R.id.btn_link);
        uploadingProgress = findViewById(R.id.prg_uploading_img);
        subItem = (SubItem) getIntent().getSerializableExtra(Consts.Extra_SubItem);
        if (subItem != null) {
            if (subItem.getSection() == SubItem.Section_Post) {
                String group_name = subItem.getName();
                group_id = subItem.getValue();
                setTitle(group_name + " -- " + getString(R.string.title_activity_publish_post));
                spinner.setVisibility(View.VISIBLE);
                tagEditText.setVisibility(View.GONE);
                titleEditText.setHint(R.string.hint_input_post_title);
                bodyEditText.setHint(R.string.hint_input_post_content);
            } else {
                setTitle(R.string.title_activity_publish_question);
                spinner.setVisibility(View.GONE);
                tagEditText.setVisibility(View.VISIBLE);
                titleEditText.setHint(R.string.hint_input_question);
                bodyEditText.setHint(R.string.hint_input_question_desc);
            }
        } else {
            ToastUtil.toast("No Data Received");
            finish();
        }
        publishButton.setOnClickListener(this);
        imgButton.setOnClickListener(this);
        insertButton.setOnClickListener(this);
        linkButton.setOnClickListener(this);
        prepare();
        tryRestoreReply();
    }


    private void tryRestoreReply() {
        String sketchTitle = "";
        String sketchContent = "";
        if (isPost()) {
            sketchTitle = SketchSharedUtil.readString(Consts.Key_Sketch_Publish_Post_Title + "_" + subItem.getValue(), "");
            sketchContent = SketchSharedUtil.readString(Consts.Key_Sketch_Publish_Post_Content + "_" + subItem.getValue(), "");
        }
        titleEditText.setText(sketchTitle);
        bodyEditText.setText(sketchContent);
    }

    private void tryClearSketch() {
        if (isPost()) {
            SketchSharedUtil.remove(Consts.Key_Sketch_Publish_Post_Content + "_" + subItem.getValue());
            SketchSharedUtil.remove(Consts.Key_Sketch_Publish_Post_Title + "_" + subItem.getValue());
        }
    }

    private void saveSketch() {
        if (!replyOK && isPost() && subItem != null) {
            if (!TextUtils.isEmpty(titleEditText.getText().toString().trim()) || !TextUtils.isEmpty(bodyEditText.getText().toString().trim())) {
                String sketchTitle = titleEditText.getText().toString();
                String sketchContent = bodyEditText.getText().toString();
                SketchSharedUtil.saveString(Consts.Key_Sketch_Publish_Post_Title + "_" + subItem.getValue(), sketchTitle);
                SketchSharedUtil.saveString(Consts.Key_Sketch_Publish_Post_Content + "_" + subItem.getValue(), sketchContent);
            } else if (TextUtils.isEmpty(titleEditText.getText().toString().trim()) && TextUtils.isEmpty(bodyEditText.getText().toString().trim())) {
                tryClearSketch();
            }
        }
    }

    @Override
    protected void onDestroy() {
        saveSketch();
        super.onDestroy();
    }

    private boolean isPost() {
        return subItem != null && subItem.getSection() == SubItem.Section_Post;
    }

    private void prepare() {
        cancelPotentialTask();
        prepareTask = new PrepareTask();
        prepareTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, group_id);
    }

    private void onReceivePreparedData(PrepareData prepareData) {
        csrf = prepareData.getCsrf();
        topics = prepareData.getPairs();
        if (isPost() && topic != null) {
            String[] items = new String[topics.size()];
            for (int i = 0; i < topics.size(); i++) {
                items[i] = topics.get(i).getName();
            }
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, R.layout.simple_spinner_item, items);
            spinner.setAdapter(arrayAdapter);
        }

    }

    private void cancelPotentialTask() {
        if (prepareTask != null && prepareTask.getStatus() == AsyncTask.Status.RUNNING) {
            prepareTask.cancel(true);
        }
    }

    private void invokeImageDialog() {
        String[] ways = {getString(R.string.add_image_from_disk),
                getString(R.string.add_image_from_camera),
                getString(R.string.add_image_from_link)};
        new AlertDialog.Builder(this).setTitle(R.string.way_to_add_image).setItems(ways, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        Intent intent = new Intent();
                        intent.setType("image/*");
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        startActivityForResult(intent, Consts.Code_Invoke_Image_Selector);
                        break;
                    case 1:
                        invokeCamera();
                        break;
                    case 2:
                        invokeImageUrlDialog();
                        break;
                }
            }
        }).create().show();
    }

    private String getPossibleUrlFromClipBoard() {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = manager.getPrimaryClip();
        String chars = "";
        if (clip != null && clip.getItemCount() > 0) {
            String tmpChars = (clip.getItemAt(0).coerceToText(this).toString()).trim();
            if (tmpChars.startsWith("http://") || tmpChars.startsWith("https://")) {
                chars = tmpChars;
            }
        }
        return chars;
    }

    private void invokeImageUrlDialog() {
        InputDialog.Builder builder = new InputDialog.Builder(this);
        builder.setTitle(R.string.input_image_url);
        builder.setCancelable(true);
        builder.setCanceledOnTouchOutside(false);
        builder.setSingleLine();
        builder.setInputText(getPossibleUrlFromClipBoard());
        builder.setOnClickListener(new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    InputDialog d = (InputDialog) dialog;
                    String text = d.InputString;
                    insertImagePath(text.trim());
                }
            }
        });
        InputDialog inputDialog = builder.create();
        inputDialog.show();
    }

    public void uploadImage(String path) {
        if (FileUtil.isImage(path)) {
            File file = new File(path);
            if (file.exists()) {
                ImageUploadTask task = new ImageUploadTask();
                task.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR, path);
            } else {
                ToastUtil.toast(R.string.file_not_exists);
            }
        } else {
            ToastUtil.toast(R.string.file_not_image);
        }
    }

    private void doneUploadingImage(String url) {
        // tap to insert image
        tmpImagePath = url;
        setImageButtonsPrepared();
    }

    /**
     * 插入图片
     */
    private void insertImagePath(String url) {
        String imgTag = "![](" + url + ")";

        SpannableString spanned = new SpannableString(imgTag);
        int size = (int) bodyEditText.getTextSize();
        int height = bodyEditText.getLineHeight();
        Bitmap sourceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_text_image);
        Bitmap bitmap = Bitmap.createBitmap(size * 10, height, Bitmap.Config.ARGB_8888);
        Matrix matrix = new Matrix();
        float scale = size / sourceBitmap.getWidth();
        matrix.setScale(scale, scale);
        matrix.postTranslate((height - size) / 2, (height - size) / 2);

        Canvas canvas = new Canvas(bitmap);

        Paint paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint1.setStyle(Paint.Style.FILL);
        paint1.setColor(Color.parseColor("#009699"));
        canvas.drawRect(0f, 0f, size * 10, height, paint1);

        Paint paint = new Paint();
        canvas.drawBitmap(sourceBitmap, matrix, paint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLUE);
        textPaint.setTextSize(size);
        canvas.drawText("图片链接...", (float) (size * 1.2), -textPaint.getFontMetrics().ascent, textPaint);

        ImageSpan imageSpan = new ImageSpan(this, bitmap, ImageSpan.ALIGN_BOTTOM);
        spanned.setSpan(imageSpan, 0, imgTag.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        bodyEditText.getText().insert(bodyEditText.getSelectionStart(), spanned);
        resetImageButtons();
    }

    File tmpUploadFile = null;

    private void invokeCamera() {
        String parentPath;
        File pFile = null;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            pFile = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        }
        if (pFile == null) {
            pFile = getFilesDir();
        }
        parentPath = pFile.getAbsolutePath();
        tmpUploadFile = new File(parentPath, System.currentTimeMillis() + ".jpg");
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Uri localUri = Uri.fromFile(tmpUploadFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, localUri);
        startActivityForResult(intent, Consts.Code_Invoke_Camera);
    }

    /**
     * 插入链接
     */
    private void insertLink() {
        InputDialog.Builder builder = new InputDialog.Builder(this);
        builder.setTitle(R.string.input_link_url);
        builder.setCancelable(true);
        builder.setCanceledOnTouchOutside(false);
        builder.setTwoLine();
        builder.setInputText(getPossibleUrlFromClipBoard());
        builder.setOnClickListener(new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    InputDialog d = (InputDialog) dialog;
                    String url = d.InputString;
                    String title = d.InputString2;
                    String result = "[" + title + "](" + url + ")";

                    SpannableString spanned = new SpannableString(result);
                    int size = (int) bodyEditText.getTextSize();
                    int height = bodyEditText.getLineHeight();
                    Bitmap sourceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.link_gray);
                    Bitmap bitmap = Bitmap.createBitmap(size * 10, height, Bitmap.Config.ARGB_8888);
                    Matrix matrix = new Matrix();
                    float scale = size / sourceBitmap.getWidth();
                    matrix.setScale(scale, scale);
                    matrix.postTranslate((height - size) / 2, (height - size) / 2);

                    Canvas canvas = new Canvas(bitmap);
                    Paint paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint1.setStyle(Paint.Style.FILL);
                    paint1.setColor(Color.parseColor("#009699"));
                    canvas.drawRect(0f, 0f, size * 10, height, paint1);

                    Paint paint = new Paint();
                    canvas.drawBitmap(sourceBitmap, matrix, paint);

                    Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    textPaint.setColor(Color.BLUE);
                    textPaint.setTextSize(size);

                    String displayed = "";
                    if (TextUtils.isEmpty(title.trim())) {
                        Uri uri = Uri.parse(url);
                        displayed = uri.getHost() + "...";
                        if (TextUtils.isEmpty(displayed)) {
                            displayed = "网络地址...";
                        }
                    } else {
                        displayed = title;
                    }

                    canvas.drawText(displayed, (float) (size * 1.2), -textPaint.getFontMetrics().ascent, textPaint);

                    ImageSpan imageSpan = new ImageSpan(PublishPostActivity.this, bitmap, ImageSpan.ALIGN_BOTTOM);
                    spanned.setSpan(imageSpan, 0, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    int start = bodyEditText.getSelectionStart();
                    bodyEditText.getText().insert(start, spanned);
                }
            }
        });
        InputDialog inputDialog = builder.create();
        inputDialog.show();
    }

    private void publish() {
        System.out.println(bodyEditText.getText().toString());
        if (TextUtils.isEmpty(titleEditText.getText().toString().trim())) {
            ToastUtil.toast(R.string.title_cannot_be_empty);
            return;
        }

        if (TextUtils.isEmpty(bodyEditText.getText().toString().trim()) && isPost()) {
            ToastUtil.toast(R.string.content_cannot_be_empty);
            return;
        }

        if (TextUtils.isEmpty(csrf)) {
            ToastUtil.toast("No csrf_token");
            return;
        }

        if (isPost()) {
            //不必检测越界行为
            topic = topics.get(spinner.getSelectedItemPosition()).getValue();
        } else {
            topic = tagEditText.getText().toString();
        }

        PublishTask task = new PublishTask();
        String title = titleEditText.getText().toString();
        String body = bodyEditText.getText().toString();
        task.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR, group_id, csrf, title, body, topic);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_publish:
                publish();
                break;
            case R.id.btn_add_img:
                invokeImageDialog();
                break;
            case R.id.btn_insert_img:
                insertImagePath(tmpImagePath);
                break;
            case R.id.btn_link:
                insertLink();
                break;
        }
    }

    class PublishTask extends AsyncTask<String, Integer, ResultObject> {

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PublishPostActivity.this);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage(getString(R.string.message_replying));
            progressDialog.show();
        }

        @Override
        protected ResultObject doInBackground(String... params) {
            String group_id = params[0];
            String csrf = params[1];
            String title = params[2];
            String body = params[3];
            String topic = params[4];
            if (isPost()) {
                return PostAPI.publishPost(group_id, csrf, title, body, topic);
            } else {
                String[] topics = topic.split(",");
                return QuestionAPI.publishQuestion(csrf, title, body, topics);
            }
        }

        @Override
        protected void onPostExecute(ResultObject resultObject) {
            progressDialog.dismiss();
            if (resultObject.ok) {
                ToastUtil.toast(R.string.reply_ok);
                setResult(RESULT_OK);
                replyOK = true;
                tryClearSketch();
                finish();
            } else {
                ToastUtil.toast(R.string.reply_failed);
            }
        }
    }

    private void resetImageButtons() {
        tmpImagePath = "";
        insertButton.setVisibility(View.GONE);
        imgButton.setVisibility(View.VISIBLE);
        uploadingProgress.setVisibility(View.GONE);
    }

    private void setImageButtonsUploading() {
        insertButton.setVisibility(View.GONE);
        imgButton.setVisibility(View.GONE);
        uploadingProgress.setVisibility(View.VISIBLE);
    }

    private void setImageButtonsPrepared() {
        insertButton.setVisibility(View.VISIBLE);
        imgButton.setVisibility(View.GONE);
        uploadingProgress.setVisibility(View.GONE);
    }

    class PrepareTask extends android.os.AsyncTask<String, Integer, ResultObject> {

        @Override
        protected ResultObject doInBackground(String... params) {
            String group_id = params[0];
            if (isPost()) {
                return PostAPI.getPostPrepareData(group_id);
            } else {
                return QuestionAPI.getQuestionPrepareData();
            }
        }

        @Override
        protected void onPostExecute(ResultObject resultObject) {
            if (resultObject.ok) {
                ToastUtil.toast(getString(R.string.get_csrf_ok));
                PrepareData prepareData = (PrepareData) resultObject.result;
                onReceivePreparedData(prepareData);
            } else {
                if (resultObject.statusCode == 403) {
                    new AlertDialog.Builder(PublishPostActivity.this).setTitle(R.string.hint)
                            .setMessage(getString(R.string.have_not_join_this_group)).setPositiveButton(R.string.ok, null).create().show();
                } else {
                    new AlertDialog.Builder(PublishPostActivity.this).setTitle(getString(R.string.get_csrf_failed))
                            .setMessage(getString(R.string.hint_reload_csrf)).setPositiveButton(R.string.ok, null).create().show();
                }
            }
        }
    }

    class ImageUploadTask extends AsyncTask<String, Integer, ResultObject> {

        @Override
        protected void onPreExecute() {
            setImageButtonsUploading();
        }

        @Override
        protected ResultObject doInBackground(String... params) {
            String path = params[0];
            return APIBase.uploadImage(path, true);
        }

        @Override
        protected void onPostExecute(ResultObject resultObject) {
            if (resultObject.ok) {
                // tap to insert image
                doneUploadingImage((String) resultObject.result);
            } else {
                resetImageButtons();
                ToastUtil.toast("Upload Failed");
            }
        }

        @Override
        protected void onCancelled() {
            resetImageButtons();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case Consts.Code_Invoke_Image_Selector:
                    Uri uri = data.getData();
                    String path = FileUtil.getActualPath(this, uri);
                    if (!TextUtils.isEmpty(path)) {
                        uploadImage(path);
                    } else {
                        //么有图
                    }
                    break;
                case Consts.Code_Invoke_Camera:
                    if (tmpUploadFile != null) {
                        uploadImage(tmpUploadFile.getAbsolutePath());
                    } else {
                        //么有图
                    }
                    break;
                default:
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_publish_post, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_reload_csrf) {
            prepare();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
