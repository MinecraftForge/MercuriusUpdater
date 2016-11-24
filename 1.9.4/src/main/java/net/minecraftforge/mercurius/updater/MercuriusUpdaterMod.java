package net.minecraftforge.mercurius.updater;

import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModClassLoader;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToFindMethodException;
import net.minecraftforge.mercurius.updater.LogHelper;
import net.minecraftforge.mercurius.updater.Utils;
import net.minecraftforge.fml.relauncher.Side;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

@Mod(modid = Constants.MODID, version = Constants.VERSION, acceptableRemoteVersions = "*")
public class MercuriusUpdaterMod
{
    @SuppressWarnings("rawtypes")
    private Class loadedMercurius;
    private Object loadedMercuriusInstance;

    @EventHandler
    public void preInit(FMLPreInitializationEvent e)
    {
        if (Loader.isModLoaded("mercurius"))
        {
            LogHelper.info("Normal Mercurius found, disabeling stub");
            return;
        }

        File librariesDir = Utils.findMaven(MinecraftForge.class, e.getSide() == Side.CLIENT);
        if (librariesDir == null)
            return;

        File libFile = Utils.updateMercurius(librariesDir, ForgeVersion.mcVersion);
        if (libFile == null)
        {
            LogHelper.fatal("Mercurius Updating failed");
            return;
        }


        try
        {
            ClassLoader cl = addClassPath(MercuriusUpdaterMod.class.getClassLoader(), libFile);
            if (cl == null)
            {
                LogHelper.fatal("Could not add Mercurius to class path! ClassLoaders:");
                cl = MercuriusUpdaterMod.class.getClassLoader();
                while (cl != null)
                {
                    LogHelper.fatal("  " + cl.getClass().getName());
                    cl = cl.getParent();
                }
                return;
            }
            loadedMercurius = cl.loadClass("net.minecraftforge.mercurius.StatsMod");
            loadedMercuriusInstance = loadedMercurius.newInstance();
        }
        catch (Exception e1)
        {
            e1.printStackTrace();
        }

        invokeEvent("preInit", e);
    }

    private ClassLoader addClassPath(ClassLoader loader, File file) throws IOException
    {
        if (loader instanceof LaunchClassLoader)
        {
            ((LaunchClassLoader)loader).addURL(file.toURI().toURL());
            return loader;
        }
        else if (loader instanceof ModClassLoader)
        {
            ((ModClassLoader)loader).addFile(file);
            return loader;
        }
        ClassLoader parent = loader.getParent();
        return parent == null ? null : addClassPath(parent, file);
    }

    @EventHandler
    public void init(FMLInitializationEvent e)
    {
        invokeEvent("init", e);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent e)
    {
        invokeEvent("postInit", e);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent e)
    {
        invokeEvent("serverStarting", e);
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent e)
    {
        invokeEvent("serverStopping", e);
    }

    @SuppressWarnings("unchecked")
    private void invokeEvent(String name, FMLEvent event)
    {
        if (loadedMercurius == null)
            return;

        try {
            ReflectionHelper.findMethod(loadedMercurius, loadedMercuriusInstance, new String[]{ name }, event.getClass())
                .invoke(loadedMercuriusInstance, event);
        } catch (UnableToFindMethodException e) {
            // No method found so its not listening for it.
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
