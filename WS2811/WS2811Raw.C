#include <iostream>
#include <stdint.h>

extern "C" {
#include <ws2811.h>
}

#include "WS2811Raw.h"

namespace
{
  bool in_use (false);

  const int TARGET_FREQ (WS2811_TARGET_FREQ);
  const int GPIO_PIN (18);
  const int DMA (5);

  ws2811_t leds;
}

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT
jboolean
JNICALL
Java_WS2811_WS2811Raw_ws2811_1init (JNIEnv *env, jclass cls, jint type, jint length)
{
  if (in_use)
    return JNI_FALSE;

  // std::cout << "In C++ land, length = " << length << std::endl;

  leds.freq = TARGET_FREQ;
  leds.dmanum = DMA;
  leds.channel[0].gpionum = GPIO_PIN;
  leds.channel[0].count = length;
  leds.channel[0].invert = 0;
  leds.channel[0].brightness = 50;     // For Chris!
  leds.channel[0].strip_type = type;
  leds.channel[1].gpionum = 0;
  leds.channel[1].count = 0;
  leds.channel[1].invert = 0;
  leds.channel[1].brightness = 0;

  ws2811_init (&leds);
  
  in_use = true;

  return JNI_TRUE;
}

JNIEXPORT
jboolean
JNICALL
Java_WS2811_WS2811Raw_ws2811_1brightness (JNIEnv *env, jclass cls, jint brightness)
{
  if (!in_use)
    return JNI_FALSE;

  // std::cout << "Set brightness " << brightness << std::endl;
  
  leds.channel[0].brightness = brightness;

  return JNI_TRUE;
}

JNIEXPORT
jboolean
JNICALL
Java_WS2811_WS2811Raw_ws2811_1update (JNIEnv *env, jclass cls, jintArray jdata)
{
  if (!in_use)
    return JNI_FALSE;
  
  jboolean copy;

  const jint len = env->GetArrayLength (jdata);

  // std::cout << "Update with " << len << " items" << std::endl;

  if (len != leds.channel[0].count)
    return JNI_FALSE;
  
  jint *raw = env->GetIntArrayElements (jdata, &copy);

  // std::cout << "Got the data, copy is " << (int) copy << std::endl;

  for (int i = 0; i < len; ++i)
    leds.channel[0].leds[i] = raw[i];

  env->ReleaseIntArrayElements (jdata, raw, 0);

  ws2811_render (&leds);
  
  return JNI_TRUE;
}

JNIEXPORT
jboolean
JNICALL
Java_WS2811_WS2811Raw_ws2811_1wait (JNIEnv *env, jclass cls)
{
  if (!in_use)
    return JNI_FALSE;

  // std::cout << "Wait" << std::endl;

  ws2811_wait (&leds);
  
  return JNI_TRUE;
}

JNIEXPORT
jboolean
JNICALL
Java_WS2811_WS2811Raw_ws2811_1close (JNIEnv *env, jclass cls)
{
  const bool result = in_use;

  // std::cout << "That's all folks!" << std::endl;
  
  in_use = false;

  ws2811_fini (&leds);
  
  return result;
}

#ifdef __cplusplus
}
#endif
