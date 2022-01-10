package com.mrtdev.editimagewithtext

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ja.burhanrashid52.photoeditor.*
import ja.burhanrashid52.photoeditor.PhotoEditor.OnSaveListener
import java.io.IOException
import java.lang.Exception


open class MainActivity : AppCompatActivity(), OnPhotoEditorListener {

    companion object {
        private const val CAMERA_REQUEST = 52
        private const val PICK_REQUEST = 53
        const val READ_WRITE_STORAGE = 52
        const val PINCH_TEXT_SCALABLE_INTENT_KEY = "PINCH_TEXT_SCALABLE"
    }

    private lateinit var mPhotoEditor: PhotoEditor
    private lateinit var mPhotoEditorView: PhotoEditorView
    private lateinit var mSaveFileHelper: FileSaveHelper

    @VisibleForTesting
    private lateinit var mSaveImageUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initView()
        mSaveFileHelper = FileSaveHelper(this)

        val pinchTextScalable = intent.getBooleanExtra(PINCH_TEXT_SCALABLE_INTENT_KEY,true)

        mPhotoEditor = PhotoEditor.Builder(this, mPhotoEditorView)
            .setPinchTextScalable(pinchTextScalable) // set flag to make text scalable when pinch
            //.setDefaultTextTypeface(mTextRobotoTf)
            //.setDefaultEmojiTypeface(mEmojiTypeFace)
            .build() // build photo editor sdk

        mPhotoEditor.setOnPhotoEditorListener(this)


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                CAMERA_REQUEST -> {
                    mPhotoEditor.clearAllViews()
                    val photo = data?.extras!!["data"] as Bitmap?
                    mPhotoEditorView?.source?.setImageBitmap(photo)
                }
                PICK_REQUEST -> try {
                    mPhotoEditor.clearAllViews()
                    val uri = data?.data
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    mPhotoEditorView?.source?.setImageBitmap(bitmap)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun initView() {
        val imgGallery: ImageView = findViewById(R.id.imgGallery)
        val imgAddText: ImageView = findViewById(R.id.imgAddText)
        val imgSave: ImageView = findViewById(R.id.imgSave)
        mPhotoEditorView = findViewById(R.id.photoEditorView)

        imgGallery.setOnClickListener {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_REQUEST)
        }

        imgAddText.setOnClickListener {
            val textEditorDialogFragment = TextEditorDialogFragment.show(this)
            textEditorDialogFragment.setOnTextEditorListener(object : TextEditorDialogFragment.TextEditor{
                override fun onDone(inputText: String?, colorCode: Int) {
                    val styleBuilder = TextStyleBuilder()
                    styleBuilder.withTextColor(colorCode)
                    mPhotoEditor.addText(inputText, styleBuilder)
                }
            })
        }

        imgSave.setOnClickListener {
            saveImage()
        }

    }

    override fun onEditTextChangeListener(rootView: View?, text: String?, colorCode: Int) {
        val textEditorDialogFragment = TextEditorDialogFragment.show(
            this,
            text!!, colorCode
        )
        textEditorDialogFragment.setOnTextEditorListener(object : TextEditorDialogFragment.TextEditor{
            override fun onDone(inputText: String?, colorCode: Int) {
                val styleBuilder = TextStyleBuilder()
                styleBuilder.withTextColor(colorCode)
//                mPhotoEditor.addText(inputText, styleBuilder)
                mPhotoEditor.editText(rootView!!, inputText, styleBuilder)
            }
        })
    }

    override fun onAddViewListener(viewType: ViewType?, numberOfAddedViews: Int) {
    }

    override fun onRemoveViewListener(viewType: ViewType?, numberOfAddedViews: Int) {
    }

    override fun onStartViewChangeListener(viewType: ViewType?) {
    }

    override fun onStopViewChangeListener(viewType: ViewType?) {
    }

    override fun onTouchSourceImage(event: MotionEvent?) {
    }

    private fun saveImage() {
        val fileName = System.currentTimeMillis().toString() + ".png"
        val hasStoragePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (hasStoragePermission || isSdkHigherThan28()) {
            mSaveFileHelper.createFile(fileName, object: FileSaveHelper.OnFileCreateResult{
                override fun onFileCreateResult(created: Boolean, filePath: String?, error: String?, Uri: Uri?) {
                    if (created) {
                        val saveSettings = SaveSettings.Builder()
                            .setClearViewsEnabled(true)
                            .setTransparencyEnabled(true)
                            .build()
                        if (checkPermission()) {
                            mPhotoEditor.saveAsFile(filePath!!,saveSettings, object : OnSaveListener {
                                    override fun onSuccess(imagePath: String) {
                                        mSaveFileHelper.notifyThatFileIsNowPubliclyAvailable(contentResolver)
                                        Toast.makeText(this@MainActivity, R.string.save_image, Toast.LENGTH_LONG).show()
                                        mSaveImageUri = Uri!!
                                        mPhotoEditorView.source.setImageURI(mSaveImageUri)
                                    }
                                    override fun onFailure(exception: Exception) {
                                        Toast.makeText(this@MainActivity, R.string.save_image_fail, Toast.LENGTH_LONG).show()
                                    }
                                })
                        }
                    } else {
                        Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                    }
                }
            })
        } else {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    open fun isSdkHigherThan28(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    open fun requestPermission(permission: String): Boolean {
        val isGranted =
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!isGranted) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), READ_WRITE_STORAGE)
        }
        return isGranted
    }

    open fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            ) {
                true
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1
                )
                false
            }
        } else {
            // for a stuff below api level 23
            true
        }
    }

}