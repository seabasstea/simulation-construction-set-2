package us.ihmc.scs2.sessionVisualizer.jfx;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.*;
import javafx.scene.paint.Color;
import us.ihmc.log.LogTools;
import us.ihmc.scs2.definition.visual.ColorDefinition;
import us.ihmc.scs2.definition.visual.VisualDefinitionFactory;
import us.ihmc.scs2.session.SessionPropertiesHelper;
import us.ihmc.scs2.sessionVisualizer.jfx.definition.JavaFXVisualTools;

import java.util.Collections;
import java.util.stream.Collectors;

public class Scene3DBuilder
{
   private final Group root = new Group();
   private final ObservableList<LightBase> allLights = FXCollections.observableArrayList();

   public static Node coordinateSystem(double size)
   {
      VisualDefinitionFactory factory = new VisualDefinitionFactory();
      factory.addCoordinateSystem(size, (ColorDefinition) null);
      return JavaFXVisualTools.collectNodes(factory.getVisualDefinitions());
   }

   private class LightsChangedListener implements ListChangeListener<Node>
   {
      @Override
      public void onChanged(Change<? extends Node> change)
      {
         while (change.next())
         {
            for (Node removedNode : change.getRemoved())
            {
               if (removedNode instanceof LightBase)
               {
                  allLights.remove((LightBase) removedNode);
               }
               else if (removedNode instanceof Group)
               {
                  Group removedGroup = (Group) removedNode;
                  removeLightsRecursive(removedGroup);
                  removedGroup.getChildren().removeListener(this);
               }
            }

            for (Node addedNode : change.getAddedSubList())
            {
               if (addedNode instanceof LightBase)
               {
                  allLights.add((LightBase) addedNode);
               }
               else if (addedNode instanceof Group)
               {
                  Group addedGroup = (Group) addedNode;
                  addLightsRecursive(addedGroup);
                  addedGroup.getChildren().addListener(this);
               }
            }
         }
      }

      private void addLightsRecursive(Node group)
      {
         for (Node child : ((Group) group).getChildren())
         {
            if (child instanceof LightBase)
            {
               allLights.add((LightBase) child);
            }
            else if (child instanceof Group)
            {
               addLightsRecursive(child);
            }
         }
      }

      private void removeLightsRecursive(Node group)
      {
         for (Node child : ((Group) group).getChildren())
         {
            if (child instanceof LightBase)
            {
               allLights.remove((LightBase) child);
            }
            else if (child instanceof Group)
            {
               removeLightsRecursive(child);
            }
         }
      }
   }

   public Scene3DBuilder()
   {
      root.getChildren().addListener(new LightsChangedListener());
   }

   /**
    * Adds some nice ambient and point lighting to the scene.
    */
   public void addDefaultLighting()
   {
      // Defaults reproduce the original flat, even lighting. Override via system properties for more
      // form-revealing shading (dark/anodized robots read as a flat silhouette under high ambient).
      double ambientValue = SessionPropertiesHelper.loadDoubleProperty("scs2.session.gui.light.ambient", 0.7);
      double pointValue = SessionPropertiesHelper.loadDoubleProperty("scs2.session.gui.light.point", 0.2);
      double pointDistance = 1000.0;
      Color ambientColor = Color.color(ambientValue, ambientValue, ambientValue);
      addNodeToView(new AmbientLight(ambientColor));
      Color indoorColor = Color.color(pointValue, pointValue, pointValue);
      addPointLight(pointDistance, pointDistance, pointDistance, indoorColor);
      addPointLight(-pointDistance, pointDistance, pointDistance, indoorColor);
      addPointLight(-pointDistance, -pointDistance, pointDistance, indoorColor);
      addPointLight(pointDistance, -pointDistance, pointDistance, indoorColor);

      // Optional directional key light (off by default): one brighter light from a chosen side creates a
      // bright->dark gradient and sweeping speculars that separate individual parts. Direction is a vector;
      // intensity is the grey level of the light colour (clamped to white).
      double keyValue = SessionPropertiesHelper.loadDoubleProperty("scs2.session.gui.light.key", 0.0);
      if (keyValue > 0.0)
      {
         double kx = SessionPropertiesHelper.loadDoubleProperty("scs2.session.gui.light.keyx", 1.0);
         double ky = SessionPropertiesHelper.loadDoubleProperty("scs2.session.gui.light.keyy", 0.5);
         double kz = SessionPropertiesHelper.loadDoubleProperty("scs2.session.gui.light.keyz", 0.8);
         double lx = kx * pointDistance, ly = ky * pointDistance, lz = kz * pointDistance;
         // A single PointLight caps at white; stack lights from the same direction so key > 1 gives a
         // genuinely brighter sun (each unit adds one full white light, the remainder a partial one).
         double remaining = keyValue;
         while (remaining > 0.0)
         {
            double c = Math.min(1.0, remaining);
            addPointLight(lx, ly, lz, Color.color(c, c, c));
            remaining -= 1.0;
         }
      }
   }

   /**
    * Add a {@link Color#WHITE} point light to the 3D view at the given coordinate.
    *
    * @param x the light x-coordinate.
    * @param y the light y-coordinate.
    * @param z the light z-coordinate.
    */
   public void addPointLight(double x, double y, double z)
   {
      addPointLight(x, y, z, Color.WHITE);
   }

   /**
    * Add a point light to the 3D view at the given coordinate.
    *
    * @param x     the light x-coordinate.
    * @param y     the light y-coordinate.
    * @param z     the light z-coordinate.
    * @param color the light color.
    */
   public void addPointLight(double x, double y, double z, Color color)
   {
      PointLight light = new PointLight(color);
      light.setTranslateX(x);
      light.setTranslateY(y);
      light.setTranslateZ(z);
      addNodeToView(light);
   }

   public void addCoordinateSystem(double scale)
   {
      addNodeToView(coordinateSystem(scale));
   }

   /**
    * Add a set of nodes to the 3D view.
    *
    * @param nodes the nodes to display.
    */
   public void addNodesToView(Iterable<? extends Node> nodes)
   {
      nodes.forEach(this::addNodeToView);
   }

   /**
    * Add a node to the 3D view.
    *
    * @param node the node to display.
    */
   public void addNodeToView(Node node)
   {
      root.getChildren().add(node);
   }

   /**
    * If true, this node (together with all its children) is completely transparent to mouse events.
    * When choosing target for mouse event, nodes with {@code mouseTransparent} set to {@code true} and
    * their subtrees won't be taken into account greatly reducing computation load especially when
    * rendering many nodes.
    *
    * @param value whether to make the entire scene mouse transparent or not.
    */
   public void setRootMouseTransparent(boolean value)
   {
      root.setMouseTransparent(value);
   }

   public ObservableList<LightBase> getAllLights()
   {
      return allLights;
   }

   /**
    * @return the root node of the scene in creation.
    */
   public Group getRoot()
   {
      return root;
   }

   public static ObservableList<LightBase> cloneAndBindLights(ObservableList<LightBase> original)
   {
      ObservableList<LightBase> clone = FXCollections.observableArrayList(original);
      setupLigthCloneList(clone, original);
      return clone;
   }

   public static ListChangeListener<LightBase> setupLigthCloneList(ObservableList<? super LightBase> listToBind, ObservableList<? extends LightBase> original)
   {
      listToBind.setAll(original.stream().map(l -> cloneAndBindLight(l)).collect(Collectors.toList()));
      ListChangeListener<LightBase> listener = new ListChangeListener<LightBase>()
      {
         @Override
         public void onChanged(Change<? extends LightBase> change)
         {
            while (change.next())
            {
               if (change.wasPermutated())
               {
                  for (int oldIndex = change.getFrom(); oldIndex < change.getTo(); oldIndex++)
                  {
                     int newIndex = change.getPermutation(oldIndex);
                     Collections.swap(listToBind, oldIndex, newIndex);
                  }
               }
               else if (change.wasUpdated())
               {
                  LogTools.error("wasUpdated is not handled!");
               }
               else if (change.wasReplaced())
               {
                  for (int i = change.getFrom(); i < change.getTo(); i++)
                  {
                     listToBind.set(i, cloneAndBindLight(change.getList().get(i)));
                  }
               }
               else
               {
                  if (change.wasRemoved())
                  {
                     listToBind.remove(change.getFrom(), change.getTo());
                  }

                  if (change.wasAdded())
                  {
                     listToBind.addAll(change.getFrom(), change.getAddedSubList().stream().map(l -> cloneAndBindLight(l)).collect(Collectors.toList()));
                  }
               }
            }
         }
      };
      original.addListener(listener);
      return listener;
   }

   @SuppressWarnings("unchecked")
   public static <L extends LightBase> L cloneAndBindLight(L original)
   {
      if (original == null)
         return null;

      L clone;
      if (original.getClass() == AmbientLight.class)
         clone = (L) new AmbientLight();
      else if (original.getClass() == PointLight.class)
         clone = (L) new PointLight();
      else
         throw new UnsupportedOperationException("Unsupported light type: " + original);

      clone.colorProperty().bindBidirectional(original.colorProperty());
      clone.getTransforms().setAll(original.getTransforms());
      clone.translateXProperty().bindBidirectional(original.translateXProperty());
      clone.translateYProperty().bindBidirectional(original.translateYProperty());
      clone.translateZProperty().bindBidirectional(original.translateZProperty());
      clone.rotateProperty().bindBidirectional(original.rotateProperty());
      clone.rotationAxisProperty().bind(original.rotationAxisProperty());

      return clone;
   }
}
