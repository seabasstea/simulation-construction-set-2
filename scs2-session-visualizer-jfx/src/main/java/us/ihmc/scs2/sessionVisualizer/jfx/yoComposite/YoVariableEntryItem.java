package us.ihmc.scs2.sessionVisualizer.jfx.yoComposite;

public class YoVariableEntryItem extends YoEntryItem
{
   private final YoComposite composite;

   public YoVariableEntryItem(YoComposite composite)
   {
      this.composite = composite;
   }

   public YoComposite getComposite()
   {
      return composite;
   }

   @Override
   public boolean isDivider()
   {
      return false;
   }

   @Override
   public boolean equals(Object o)
   {
      if (this == o)
         return true;
      if (!(o instanceof YoVariableEntryItem other))
         return false;
      return composite.equals(other.composite);
   }

   @Override
   public int hashCode()
   {
      return composite.hashCode();
   }
}
