package us.ihmc.scs2.sessionVisualizer.jfx.controllers.menu;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import us.ihmc.scs2.sessionVisualizer.jfx.AboutWindowController;
import us.ihmc.scs2.sessionVisualizer.jfx.SessionVisualizerIOTools;
import us.ihmc.scs2.sessionVisualizer.jfx.UpdateWindowController;
import us.ihmc.scs2.sessionVisualizer.jfx.controllers.VisualizerController;
import us.ihmc.scs2.sessionVisualizer.jfx.managers.SessionVisualizerWindowToolkit;
import us.ihmc.scs2.sessionVisualizer.jfx.version.SCS2VersionChecker;

import java.io.IOException;

public class HelpMenuController implements VisualizerController
{
   private SessionVisualizerWindowToolkit toolkit;

   @Override
   public void initialize(SessionVisualizerWindowToolkit toolkit)
   {
      this.toolkit = toolkit;
   }

   @FXML
   public void checkForUpdates()
   {
      SCS2VersionChecker.resetCache();

      Thread checkThread = new Thread(() ->
      {
         try
         {
            boolean isLatest = SCS2VersionChecker.isLatestRelease();

            Platform.runLater(() ->
            {
               if (!isLatest)
               {
                  openUpdateWindow();
               }
               else
               {
                  Alert alert = new Alert(AlertType.INFORMATION);
                  alert.setTitle("Software Update");
                  alert.setHeaderText(null);
                  alert.setContentText("You are running the latest version (" + SCS2VersionChecker.getCurrentBaseVersion() + ").");
                  SessionVisualizerIOTools.addSCSIconToDialog(alert);
                  alert.initOwner(toolkit.getWindow());
                  alert.showAndWait();
               }
            });
         }
         catch (Exception e)
         {
            e.printStackTrace();
            Platform.runLater(() ->
            {
               Alert alert = new Alert(AlertType.WARNING);
               alert.setTitle("Software Update");
               alert.setHeaderText(null);
               alert.setContentText("Could not check for updates. Please check your internet connection.");
               SessionVisualizerIOTools.addSCSIconToDialog(alert);
               alert.initOwner(toolkit.getWindow());
               alert.showAndWait();
            });
         }
      });
      checkThread.setDaemon(true);
      checkThread.start();
   }

   private void openUpdateWindow()
   {
      try
      {
         FXMLLoader loader = new FXMLLoader(SessionVisualizerIOTools.UPDATE_WINDOW_URL);
         loader.load();
         UpdateWindowController controller = loader.getController();
         controller.initialize(toolkit);
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }

   @FXML
   public void openAboutDialog()
   {
      try
      {
         FXMLLoader loader = new FXMLLoader(SessionVisualizerIOTools.ABOUT_WINDOW_URL);
         loader.load();
         AboutWindowController controller = loader.getController();
         controller.initialize(toolkit);
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
   }
}
