package us.ihmc.scs2.sessionVisualizer.jfx.controllers.menu;

import javafx.fxml.FXML;
import javafx.scene.control.CheckMenuItem;
import us.ihmc.messager.javafx.JavaFXMessager;
import us.ihmc.scs2.sessionVisualizer.jfx.SessionVisualizerTopics;
import us.ihmc.scs2.sessionVisualizer.jfx.controllers.VisualizerController;
import us.ihmc.scs2.sessionVisualizer.jfx.managers.SessionVisualizerWindowToolkit;

public class ViewMenuController implements VisualizerController
{
   @FXML
   private CheckMenuItem showLeftSidebarMenuItem;
   @FXML
   private CheckMenuItem showRightSidebarMenuItem;

   @Override
   public void initialize(SessionVisualizerWindowToolkit toolkit)
   {
      JavaFXMessager messager = toolkit.getMessager();
      SessionVisualizerTopics topics = toolkit.getTopics();
      messager.bindBidirectional(topics.getShowLeftSidebar(), showLeftSidebarMenuItem.selectedProperty(), false);
      messager.bindBidirectional(topics.getShowRightSidebar(), showRightSidebarMenuItem.selectedProperty(), false);
   }
}
