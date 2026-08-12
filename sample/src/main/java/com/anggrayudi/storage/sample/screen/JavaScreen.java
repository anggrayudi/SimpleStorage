package com.anggrayudi.storage.sample.screen;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.anggrayudi.storage.sample.ActivityUtils;
import com.anggrayudi.storage.StorageFile;
import com.anggrayudi.storage.StoragePath;
import com.anggrayudi.storage.file.CreateMode;
import com.anggrayudi.storage.file.MimeType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * The synchronous half of v3 is ordinary Java: factories are static, and metadata, navigation,
 * creation and streams are plain method calls. The transfer operations are suspend functions and
 * are not callable from here — see JAVA_COMPATIBILITY.md.
 */
public class JavaScreen extends AppCompatActivity {

  private TextView output;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTitle("Calling v3 from Java");

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    int padding = (int) (16 * getResources().getDisplayMetrics().density);
    root.setPadding(padding, padding, padding, padding);

    Button createButton = new Button(this);
    createButton.setText("Create a file and read it back");
    createButton.setOnClickListener(v -> createAndRead());
    root.addView(createButton);

    Button listButton = new Button(this);
    listButton.setText("List the app folder");
    listButton.setOnClickListener(v -> listFolder());
    root.addView(listButton);

    output = new TextView(this);
    output.setPadding(0, padding, 0, 0);
    root.addView(output);

    ScrollView scroll = new ScrollView(this);
    scroll.addView(root);
    setContentView(scroll);
    // Same edge-to-edge handling the Kotlin screens get from SampleScreen; without it the content
    // draws behind the status bar and the action bar.
    ActivityUtils.applyEdgeToEdgeContentInsets(this);
  }

  private StorageFile appFolder() {
    return StorageFile.from(this, getExternalFilesDir(null));
  }

  private void createAndRead() {
    StorageFile file =
        appFolder().createFile("from-java.txt", MimeType.TEXT, CreateMode.REPLACE);
    if (file == null) {
      output.setText("Could not create the file");
      return;
    }
    try (OutputStream out = file.openOutputStream(false)) {
      out.write("written from Java".getBytes());
    } catch (IOException e) {
      output.setText("Write failed: " + e);
      return;
    }
    try (InputStream in = file.openInputStream()) {
      byte[] bytes = new byte[(int) file.getLength()];
      //noinspection ResultOfMethodCallIgnored
      in.read(bytes);
      output.setText(
          "Created " + file.getName() + " (" + file.getLength() + " bytes)\nContent: " + new String(bytes));
    } catch (IOException e) {
      output.setText("Read failed: " + e);
    }
  }

  private void listFolder() {
    StoragePath path = appFolder().getPath();
    List<StorageFile> children = appFolder().list();
    StringBuilder text = new StringBuilder("Path: " + path + "\n");
    for (StorageFile child : children) {
      text.append(child.isDirectory() ? "[dir] " : "").append(child.getName()).append('\n');
    }
    output.setText(text.toString());
  }
}
