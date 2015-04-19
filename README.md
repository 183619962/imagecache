##关于图片加载
 - imagecache.jar

>详细的使用方法可见[CommonAdapter](https://github.com/183619962/CommonAdapter)中有使用。
单纯使用，将根目录下的 imagecache.jar 导入到个人项目，按如下方法使用即可，2句话即可完成图片加载及缓存。

###使用方法

**实例化ImageFetcher（两种方式）**
#####1.指定图片的缩放宽高
- ImageFetcher imageFetcher1=ImageFetcher.getInstance(Context context,int imageWidth, int imageHeight, String imageCacheDir,float memCacheSize);

###### 参数解释
 1.	content  上下文对象  如：MainActivity.this
 2.	imageWidth  图片处理的宽度 如：100
 3.	imageHeight 图片处理的高度 如：100
 4.	imageCacheDir 图片缓存的文件夹名称 如：‘MyImagecache’
 5. memCacheSize 缓存区间的大小 如：0.25f 表示磁盘大小的25%





#####2.指定一个参数，相同的高宽
- ImageFetcher imageFetcher=ImageFetcher.getInstance(Context context, int imageThumSize,String imageCacheDir,float memCacheSize);

###### 参数解释
 1. content  上下文对象  如：MainActivity.this
 2. imageThumSize  图片处理的size 如：100（实际是高=宽=100）
 3. imageCacheDir 图片缓存的文件夹名称 如：‘MyImagecache’
 4. memCacheSize 缓存区间的大小 如：0.25f 表示磁盘大小的25%


**加载图片**

imageFetcher.loadImage(String url, ImageView imageView);