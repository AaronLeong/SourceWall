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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.sourcewall.connection.ResultObject;
import com.example.sourcewall.connection.api.APIBase;
import com.example.sourcewall.dialogs.InputDialog;
import com.example.sourcewall.model.AceModel;
import com.example.sourcewall.model.Article;
import com.example.sourcewall.model.Post;
import com.example.sourcewall.model.Question;
import com.example.sourcewall.model.UComment;
import com.example.sourcewall.util.Consts;
import com.example.sourcewall.util.FileUtil;
import com.example.sourcewall.util.RegUtil;
import com.example.sourcewall.util.SketchSharedUtil;
import com.example.sourcewall.util.ToastUtil;

import java.io.File;

/**
 * 格式使用的应该是UBB代码
 */
public class ReplyActivity extends SwipeActivity implements View.OnClickListener {

    private EditText editText;
    private TextView hostText;
    private AceModel aceModel;
    private ImageButton imgButton;
    private ImageButton insertButton;
    private View uploadingProgress;
    private ProgressDialog progressDialog;
    private String tmpImagePath;
    private UComment comment;
    private boolean replyOK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reply);
        Toolbar toolbar = (Toolbar) findViewById(R.id.action_bar);
        setSupportActionBar(toolbar);
        aceModel = (AceModel) getIntent().getSerializableExtra(Consts.Extra_Ace_Model);
        comment = (UComment) getIntent().getSerializableExtra(Consts.Extra_Simple_Comment);
        editText = (EditText) findViewById(R.id.text_reply);
        hostText = (TextView) findViewById(R.id.text_reply_host);
        if (comment != null) {
            hostText.setVisibility(View.VISIBLE);
            String cont = RegUtil.html2PlainTextWithoutBlockQuote(comment.getContent());
            if (cont.length() > 100) {
                cont = cont.substring(0, 100) + "...";
            }
            hostText.setText("引用@" + comment.getAuthor() + " 的话：" + cont);
        }
        if (aceModel instanceof Question) {
            setTitle("回答问题");
            editText.setHint(R.string.hint_answer);
        }
        ImageButton publishButton = (ImageButton) findViewById(R.id.btn_publish);
        imgButton = (ImageButton) findViewById(R.id.btn_add_img);
        insertButton = (ImageButton) findViewById(R.id.btn_insert_img);
        ImageButton linkButton = (ImageButton) findViewById(R.id.btn_link);
        uploadingProgress = findViewById(R.id.prg_uploading_img);
        publishButton.setOnClickListener(this);
        imgButton.setOnClickListener(this);
        insertButton.setOnClickListener(this);
        linkButton.setOnClickListener(this);
        tryRestoreReply();
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
            if (new File(path).isFile()) {
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
        tmpImagePath = url;
        setImageButtonsPrepared();
    }

    /**
     * 插入图片
     */
    private void insertImagePath(String url) {
        String imgTag = "[image]" + url + "[/image]";
        SpannableString spanned = new SpannableString(imgTag);
        int size = (int) editText.getTextSize();
        int height = editText.getLineHeight();
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
        editText.getText().insert(editText.getSelectionStart(), spanned);
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
                    String result = "[url=" + url + "]" + title + "[/url]";

                    SpannableString spanned = new SpannableString(result);
                    int size = (int) editText.getTextSize();
                    int height = editText.getLineHeight();
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

                    ImageSpan imageSpan = new ImageSpan(ReplyActivity.this, bitmap, ImageSpan.ALIGN_BOTTOM);
                    spanned.setSpan(imageSpan, 0, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    editText.getText().insert(editText.getSelectionStart(), spanned);
                }
            }
        });
        InputDialog inputDialog = builder.create();
        inputDialog.show();
    }

    private void publishReply(String rep) {
        PublishReplyTask task = new PublishReplyTask();
        String header = "";
        if (comment != null) {
            header = "[blockquote]" + hostText.getText() + "[/blockquote]";
        }
        task.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR, header, rep);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_publish:
                if (!TextUtils.isEmpty(editText.getText().toString().trim())) {
                    publishReply(editText.getText().toString());
                } else {
                    ToastUtil.toast(R.string.content_cannot_be_empty);
                }
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

    class PublishReplyTask extends AsyncTask<String, Integer, ResultObject> {

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(ReplyActivity.this);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage(getString(R.string.message_replying));
            progressDialog.show();
        }

        @Override
        protected ResultObject doInBackground(String... params) {
            String header = params[0];
            String content = params[1];
            return APIBase.reply(aceModel, header + content);
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

    private void tryRestoreReply() {
        String content = "";
        if (aceModel != null) {
            if (aceModel instanceof Article) {
                content = SketchSharedUtil.readString(Consts.Key_Sketch_Article_Reply + "_" + ((Article) aceModel).getId(), "");
            } else if (aceModel instanceof Post) {
                content = SketchSharedUtil.readString(Consts.Key_Sketch_Post_Reply + "_" + ((Post) aceModel).getId(), "");
            } else if (aceModel instanceof Question) {
                content = SketchSharedUtil.readString(Consts.Key_Sketch_Question_Answer + "_" + ((Question) aceModel).getId(), "");
            }
        }
        editText.setText(content);
    }

    private void tryClearSketch() {
        if (aceModel instanceof Article) {
            SketchSharedUtil.remove(Consts.Key_Sketch_Article_Reply + "_" + ((Article) aceModel).getId());
        } else if (aceModel instanceof Post) {
            SketchSharedUtil.remove(Consts.Key_Sketch_Post_Reply + "_" + ((Post) aceModel).getId());
        } else if (aceModel instanceof Question) {
            SketchSharedUtil.remove(Consts.Key_Sketch_Question_Answer + "_" + ((Question) aceModel).getId());
        }
    }

    private void saveSketch() {
        if (!replyOK && !TextUtils.isEmpty(editText.getText().toString().trim()) && aceModel != null) {
            String sketch = editText.getText().toString();
            if (aceModel instanceof Article) {
                SketchSharedUtil.saveString(Consts.Key_Sketch_Article_Reply + "_" + ((Article) aceModel).getId(), sketch);
            } else if (aceModel instanceof Post) {
                SketchSharedUtil.saveString(Consts.Key_Sketch_Post_Reply + "_" + ((Post) aceModel).getId(), sketch);
            } else if (aceModel instanceof Question) {
                SketchSharedUtil.saveString(Consts.Key_Sketch_Question_Answer + "_" + ((Question) aceModel).getId(), sketch);
            }
        } else if (!replyOK && TextUtils.isEmpty(editText.getText().toString().trim())) {
            tryClearSketch();
        }
    }

    @Override
    protected void onDestroy() {
        saveSketch();
        super.onDestroy();
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
                if (tmpUploadFile != null && tmpUploadFile.exists()) {
                    tmpUploadFile.delete();
                }
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
        getMenuInflater().inflate(R.menu.menu_reply_article, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }


}
