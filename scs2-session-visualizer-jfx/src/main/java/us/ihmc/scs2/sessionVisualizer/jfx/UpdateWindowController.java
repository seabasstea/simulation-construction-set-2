package us.ihmc.scs2.sessionVisualizer.jfx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import us.ihmc.scs2.sessionVisualizer.jfx.controllers.VisualizerController;
import us.ihmc.scs2.sessionVisualizer.jfx.managers.SessionVisualizerWindowToolkit;
import us.ihmc.scs2.sessionVisualizer.jfx.tools.JavaFXMissingTools;
import us.ihmc.scs2.sessionVisualizer.jfx.version.SCS2VersionChecker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class UpdateWindowController implements VisualizerController
{
   @FXML
   private Stage stage;
   @FXML
   private Text currentVersionText;
   @FXML
   private Text availableVersionText;
   @FXML
   private ProgressBar downloadProgressBar;
   @FXML
   private Label statusLabel;
   @FXML
   private Button updateButton;
   @FXML
   private Button cancelButton;

   @Override
   public void initialize(SessionVisualizerWindowToolkit toolkit)
   {
      SessionVisualizerIOTools.addSCSIconToWindow(stage);
      stage.initOwner(toolkit.getWindow());

      currentVersionText.setText(SCS2VersionChecker.getCurrentBaseVersion());
      availableVersionText.setText(SCS2VersionChecker.getLatestBaseVersion());

      stage.show();
      JavaFXMissingTools.centerWindowInOwner(stage, toolkit.getWindow());
   }

   @FXML
   private void onUpdateNow()
   {
      String msiUrl = SCS2VersionChecker.getLatestMsiAssetUrl();
      if (msiUrl == null)
      {
         statusLabel.setText("No MSI installer found in the latest release.");
         return;
      }

      updateButton.setDisable(true);
      downloadProgressBar.setVisible(true);
      statusLabel.setText("Downloading update...");

      Thread downloadThread = new Thread(() -> downloadAndInstall(msiUrl));
      downloadThread.setDaemon(true);
      downloadThread.start();
   }

   private void downloadAndInstall(String msiUrl)
   {
      try
      {
         Path tempFile = Files.createTempFile("SCS2SessionVisualizer-update-", ".msi");

         OkHttpClient client = new OkHttpClient();
         Request request = new Request.Builder().url(msiUrl).build();

         try (Response response = client.newCall(request).execute())
         {
            if (!response.isSuccessful())
               throw new IOException("Download failed: " + response);

            ResponseBody body = response.body();
            long contentLength = body.contentLength();

            try (InputStream in = body.byteStream(); OutputStream out = Files.newOutputStream(tempFile))
            {
               byte[] buffer = new byte[8192];
               long totalRead = 0;
               int bytesRead;

               while ((bytesRead = in.read(buffer)) != -1)
               {
                  out.write(buffer, 0, bytesRead);
                  totalRead += bytesRead;

                  if (contentLength > 0)
                  {
                     double progress = (double) totalRead / contentLength;
                     Platform.runLater(() ->
                     {
                        downloadProgressBar.setProgress(progress);
                        statusLabel.setText(String.format("Downloading... %.0f%%", progress * 100));
                     });
                  }
               }
            }
         }

         Platform.runLater(() -> statusLabel.setText("Download complete. Launching installer..."));

         ProcessBuilder pb = new ProcessBuilder("msiexec", "/i", tempFile.toAbsolutePath().toString());
         pb.start();

         Platform.runLater(() ->
         {
            stage.close();
            System.exit(0);
         });
      }
      catch (IOException e)
      {
         e.printStackTrace();
         Platform.runLater(() ->
         {
            statusLabel.setText("Download failed: " + e.getMessage());
            updateButton.setDisable(false);
            downloadProgressBar.setVisible(false);
         });
      }
   }

   public Stage getStage()
   {
      return stage;
   }

   public void populateVersionLabels()
   {
      currentVersionText.setText(SCS2VersionChecker.getCurrentBaseVersion());
      availableVersionText.setText(SCS2VersionChecker.getLatestBaseVersion());
   }

   @FXML
   private void onCancel()
   {
      stage.close();
   }
}
