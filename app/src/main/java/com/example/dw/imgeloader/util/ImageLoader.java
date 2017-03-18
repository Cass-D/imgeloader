package com.example.dw.imgeloader.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.DisplayMetrics;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;


/**
 * Created by dw on 2017-3-18.
 * 图片加载类
 */

public class ImageLoader {
    private  static ImageLoader instance;
    private LruCache<String, Bitmap> lruCache;
    private ExecutorService threadPool;
    private static final int DEFAULT_THREAD_COUNT = 1;
    private Type type = Type.LIFO;
    //taskQuery，LinkedList采用链表，且适合频繁地访问中间和尾部
    private LinkedList<Runnable> taskQueue;
    //后台轮询线程
    private Thread poolThread;
    private Handler poolThreadHandler;
    private Handler UIHandler;
    private Semaphore semaphorePoolThreadHandler = new Semaphore(0);
    private Semaphore semaphoreThreadPool;
    public enum Type {
        FIFO, LIFO
    }
    private ImageLoader(int threadCount, Type type) {
        init(threadCount, type);
    }

    /**
     * 初始化操作
     *
     * @param threadCount
     * @param type
     */
    private void init(int threadCount, Type type) {
        // 后台轮询线程
        poolThread = new Thread() {
            @Override
            public void run() {
                Looper.prepare();

                poolThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        // 线程池去取出一个任务进行执行
                        threadPool.execute(getTask());
                        try {
                            semaphoreThreadPool.acquire();
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }

                };
                //poolThreadHandler初始化完毕，释放信号量
                semaphorePoolThreadHandler.release();
                Looper.loop();
            }
        };

        poolThread.start();

        // 获取应用的最大可用内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheMemory = maxMemory / 8;

        lruCache = new LruCache<String, Bitmap>(cacheMemory) {
            protected int sizeOf(String key, Bitmap value) {
                // 返回每个bitmap所占据的内存，每一行占据的字节数×高度
                return value.getRowBytes() * value.getHeight();
            }
        };

        // 创建线程池
        threadPool = Executors.newFixedThreadPool(threadCount);
        taskQueue = new LinkedList<Runnable>();
        this.type = type;

        semaphoreThreadPool = new Semaphore(threadCount);
    }

    /**
     * 从任务队列取出一个方法
     *
     * @return
     */
    private Runnable getTask() {

        if (type == Type.FIFO) {
            return taskQueue.removeFirst();
        } else if (type == Type.LIFO) {
            return taskQueue.removeLast();
        }

        return null;
    }

    /**
     * 单例模式，整个程序只能有一个缓存处理
     *
     * @return
     */
    public static ImageLoader getInstance() {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) {
                    instance = new ImageLoader(DEFAULT_THREAD_COUNT, Type.LIFO);
                }
            }
        }
        return instance;
    }

    public static ImageLoader getInstance(int threadCount, Type type) {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) {
                    instance = new ImageLoader(threadCount, type);
                }
            }
        }
        return instance;
    }

    /**
     * 根据path为imageView设置图片 imageView是重复使用的，为避免显示错乱，imageView需要setTag()
     *
     * @param path
     * @param imageView
     */
    public void loadImage(final String path, final ImageView imageView) {
        imageView.setTag(path);

        if (UIHandler == null) {
            UIHandler = new Handler() {
                public void handleMessage(Message msg) {
                    // 获取得到的图片并设置
                    ImgBeanHolder holder = (ImgBeanHolder) msg.obj;
                    Bitmap bm = holder.bitmap;
                    ImageView imageView = holder.imageView;
                    String path = holder.path;

                    if (imageView.getTag().toString().equals(path)) {
                        imageView.setImageBitmap(bm);
                    }
                };
            };
        }

        Bitmap bm = this.getBitmapFromLruCache(path);

        if (bm != null) {
            reFrashBitmap(path, imageView, bm);
        } else {
            addTasks(new Runnable() {

                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    // 加载图片,实现图片的压缩
                    // 1、获得图片需要显示的大小
                    ImageSize imageSize = getImageViewSize(imageView);
                    // 2、压缩图片
                    Bitmap bm = decodeSampleBitmapFromPath(imageSize.width,
                            imageSize.height, path);
                    //3、把图片加入到缓存
                    addBitmapToLruCache(path, bm);

                    reFrashBitmap(path, imageView, bm);

                    semaphoreThreadPool.release();
                }

            });
        }
    }

    private void reFrashBitmap(final String path,
                               final ImageView imageView, Bitmap bm) {
        Message message = Message.obtain();
        ImgBeanHolder holder = new ImgBeanHolder();
        holder.bitmap = bm;
        holder.path = path;
        holder.imageView = imageView;

        message.obj = holder;
        UIHandler.sendMessage(message);
    }
    /**
     * 将图片加如缓存LruCache
     * @param path
     * @param bm
     */
    protected void addBitmapToLruCache(String path, Bitmap bm) {

        if(getBitmapFromLruCache(path) == null) {
            if(bm != null) {
                lruCache.put(path, bm);
            }
        }
    }

    /**
     * 根据图片需要显示的宽和高进行压缩
     *
     * @param width
     * @param height
     * @param path
     * @return
     */
    protected Bitmap decodeSampleBitmapFromPath(int width, int height,
                                                String path) {
        // 为获取图片的宽和高，并不把图片加载到内存中,需要设置injustDecodeBound为true
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options); // option获得宽高

        options.inSampleSize = caculateInSampleSize(options, width, height);

        //使用获得的InSampleSize再次解析图片并把图片加载到内存
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);

        return bitmap;
    }

    /**
     * 根据需求的宽和高以及实际的宽和高计算sampleSize,即压缩比
     *
     * @param options
     * @param
     * @param
     * @return
     */
    private int caculateInSampleSize(BitmapFactory.Options options, int reqWidth,
                                     int reqHeight) {

        int width = options.outWidth;
        int height = options.outHeight;

        int inSampleSize = 1;

        if (width > reqWidth || height > reqHeight) {
            int widthRadio = Math.round(width * 1.0f / reqWidth);
            int heightRadio = Math.round(height * 1.0f / reqHeight);

            inSampleSize = Math.max(widthRadio, heightRadio);
        }
        return inSampleSize;
    }

    /**
     * 根据ImageVIew获取适当的宽高 如果是固定值，可直接获取图片宽高 如果设置上wrap_content等，则需要getLayoutParams
     *
     * @param imageView
     * @return
     */
    protected ImageSize getImageViewSize(ImageView imageView) {

        ImageSize imageSize = new ImageSize();

        DisplayMetrics metrics = imageView.getContext().getResources()
                .getDisplayMetrics();

        LayoutParams ip = imageView.getLayoutParams();

        int width = imageView.getWidth(); // 获取实际宽度
        if (width <= 0) {
            width = ip.width; // 获取在layout中声明的宽度
        }

        if (width <= 0) {
            width = getImageFieldValue(imageView, "mMaxWidth");; // 检查最大值
        }

        if (width <= 0) {
            width = metrics.widthPixels;
        }

        int height = imageView.getWidth(); // 获取实际高度
        if (height <= 0) {
            height = ip.width; // 获取在layout中声明的宽度
        }

        if (height <= 0) {
            height = getImageFieldValue(imageView, "mMaxHeight"); // 检查最大值
        }

        if (height <= 0) {
            height = metrics.widthPixels;
        }

        imageSize.height = height;

        imageSize.width = width;

        return imageSize;
    }
    /**
     * 通过反射获得某个属性值
     * @param object
     * @return
     */
    private static int getImageFieldValue(Object object, String fieldName) {
        int value = 0;

        try {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            int fieldValue = field.getInt(object);
            if(fieldValue > 0 && fieldValue < Integer.MAX_VALUE) {
                value = fieldValue;
            }
        }  catch (NoSuchFieldException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return value;
    }

    private synchronized void addTasks(Runnable runnable) {
        taskQueue.add(runnable);

        try {
            if(poolThreadHandler == null) {
                semaphorePoolThreadHandler.acquire();
            }
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        poolThreadHandler.sendEmptyMessage(0x110);
    }

    /**
     * 根据key在缓存中获取bitmap
     *
     * @param key
     * @return
     */
    private Bitmap getBitmapFromLruCache(String key) {
        return lruCache.get(key);
    }
    private class ImageSize {
        int width;
        int height;
    }

    private class ImgBeanHolder {
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }
}
