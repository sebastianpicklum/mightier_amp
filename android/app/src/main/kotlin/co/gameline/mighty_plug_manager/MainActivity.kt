// (c) 2020 Dian Iliev (Tuntorius)
// This code is licensed under MIT license (see LICENSE.md for details)

package co.gameline.mighty_plug_manager

import android.os.Bundle

import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugins.GeneratedPluginRegistrant
import io.flutter.embedding.engine.plugins.shim.ShimPluginRegistry

import io.flutter.view.FlutterMain
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

import android.content.Intent
import android.app.Activity
import android.net.Uri

import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

import java.nio.ShortBuffer

class MainActivity: FlutterActivity() {
    
    internal var WRITE_REQUEST_CODE = 77777 //unique request code
    internal var OPEN_REQUEST_CODE = 22222
    internal var _result: Result? = null
    internal var _data: String? = null

    private val CHANNEL = "mighty_plug/decoder"
    var decoder = MediaDecoder();

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        GeneratedPluginRegistrant.registerWith(flutterEngine);

        configureDecoderAPI(flutterEngine);
        configureFileSaveAPI(flutterEngine);
    }

    fun configureDecoderAPI(@NonNull flutterEngine: FlutterEngine)
    {
        MethodChannel(flutterEngine.getDartExecutor(), CHANNEL).setMethodCallHandler { methodCall, result ->
            var arguments = methodCall.arguments<Map<String, String>>();
            if (methodCall.method == "open")
            {
                decoder.open(arguments["path"]);
                result.success(null);
            }
            else if (methodCall.method == "next") {
                if (decoder != null) {
                    var buffer = decoder.readShortData();
                    result.success(buffer);
                }
                else
                    result.error("decoder_unavailable", "you must open a file first", null);
            }
            else if (methodCall.method == "close") {
                if (decoder != null)
                    decoder.release();
                result.success(null);
            }
            else if (methodCall.method == "duration") {
                if (decoder != null) {
                    var dur = decoder.getDuration();
                    result.success(dur);
                }
                else
                    result.error("decoder_unavailable", "you must open a file first", null);
            }
            else if (methodCall.method == "sampleRate") {
                if (decoder != null) {
                    var sr = decoder.getSampleRate();
                    result.success(sr);
                }
                else
                    result.error("decoder_unavailable", "you must open a file first", null);
            }
        }
    }

    fun configureFileSaveAPI(@NonNull flutterEngine: FlutterEngine)
    {
        MethodChannel(flutterEngine.getDartExecutor(), "com.msvcode.filesaver/files").setMethodCallHandler { call, result ->
            // Note: this method is invoked on the main thread.
            if (call.method == "saveFile") {
                _result = result
                _data = call.argument<String>("data")
                var mime:String? = call.argument<String?>("mime");
                var name:String? = call.argument<String?>("name");
                if (mime!=null && name!=null)
                    createFile(mime, name)
            } else if (call.method == "openFile") {
                _result = result
                var mime:String? = call.argument<String?>("mime");
                if (mime!=null)
                    openFile(mime)
            } else {
                result.notImplemented()
            }
        }
    }

    private fun createFile(mimeType: String, fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            // Filter to only show results that can be "opened", such as
            // a file (as opposed to a list of contacts or timezones).
            addCategory(Intent.CATEGORY_OPENABLE)

            // Create a file with the requested MIME type.
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        startActivityForResult(intent, WRITE_REQUEST_CODE)
    }

    //replace with ACTION_GET_CONTENT for just a temporary access
    private fun openFile(mimeType: String) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            // Filter to only show results that can be "opened", such as
            // a file (as opposed to a list of contacts or timezones).
            addCategory(Intent.CATEGORY_OPENABLE)

            // Create a file with the requested MIME type.
            type = mimeType
        }

        startActivityForResult(intent, OPEN_REQUEST_CODE)
    }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    // Check which request we're responding to
    if (requestCode == WRITE_REQUEST_CODE) {
        // Make sure the request was successful
        if (resultCode == Activity.RESULT_OK) {
          if (data != null && data.getData() != null) {
            //now write the data
            writeInFile(data.getData()) //data.getData() is Uri
          } else {
            _result?.error("NO DATA", "No data", null)
          }
        } else {
          _result?.error("CANCELED", "User cancelled", null)
        }
    }
    else if (requestCode == OPEN_REQUEST_CODE) {
        if (resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                //now write the data
                readFile(data.getData())
            }else {
                _result?.error("NO DATA", "No data", null)
            }
        } else {
            _result?.error("CANCELED", "User cancelled", null)
        }
    }
  }

  private fun writeInFile(uri: Uri) {
    val outputStream: OutputStream
    try {
      outputStream = getContentResolver().openOutputStream(uri)
      val bw = BufferedWriter(OutputStreamWriter(outputStream))
      bw.write(_data)
      bw.flush()
      bw.close()
      _result?.success("SUCCESS");
    } catch (e:Exception){
      _result?.error("ERROR", "Unable to write", null)
    }
  }

  private fun readFile(uri: Uri) {
      val inputStream: InputStreamReader
      try {
            inputStream = InputStreamReader(getContentResolver().openInputStream(uri))
            val br = BufferedReader(inputStream)
            val fileContent = br.use { inputStream.readText() }

            br.close()
            _result?.success(fileContent)
      } catch (e:Exception){
      _result?.error("ERROR", "Unable to read", null)
    }
  }
}
