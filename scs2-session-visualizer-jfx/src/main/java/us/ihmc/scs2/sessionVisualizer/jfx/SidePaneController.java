package us.ihmc.scs2.sessionVisualizer.jfx;

import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import us.ihmc.scs2.sessionVisualizer.jfx.controllers.yoComposite.entry.YoEntryTabPaneController;
import us.ihmc.scs2.sessionVisualizer.jfx.controllers.yoComposite.search.YoSearchTabPaneController;
import us.ihmc.scs2.sessionVisualizer.jfx.managers.SessionVisualizerToolkit;

public class SidePaneController
{
   @FXML
   private TabPane yoSearchTabPane;
   @FXML
   private VBox yoEntryTabPane; // Root of YoEntryTabPane.fxml: a VBox wrapping the tab navigator + the TabPane (issue #168).
   @FXML
   private YoSearchTabPaneController yoSearchTabPaneController;
   @FXML
   private YoEntryTabPaneController yoEntryTabPaneController;

   public void initialize(SessionVisualizerToolkit toolkit)
   {
      yoSearchTabPaneController.initialize(toolkit);
      yoEntryTabPaneController.initialize(toolkit);
   }

   public void start()
   {
      yoSearchTabPaneController.start();
      yoEntryTabPaneController.start();
   }

   public void stop()
   {
      yoSearchTabPaneController.stop();
      yoEntryTabPaneController.stop();
   }

   public YoSearchTabPaneController getYoSearchTabPaneController()
   {
      return yoSearchTabPaneController;
   }

   public YoEntryTabPaneController getYoEntryTabPaneController()
   {
      return yoEntryTabPaneController;
   }
}
