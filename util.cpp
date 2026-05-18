/*
 * Bermuda Syndrome engine rewrite
 * Copyright (C) 2007-2011 Gregory Montoir
 */

#include <cstdarg>
#ifdef __ANDROID__
#include <android/log.h>
#endif
#include "util.h"

uint16_t g_debugMask;

void debug(uint16_t cm, const char *msg, ...) {
	char buf[1024];
	if (cm & g_debugMask) {
		va_list va;
		va_start(va, msg);
		vsprintf(buf, msg, va);
		va_end(va);
#ifdef __ANDROID__
		__android_log_print(ANDROID_LOG_INFO, "BSEngine", "%s", buf);
#endif
		printf("%s\n", buf);
		fflush(stdout);
	}
}

void error(const char *msg, ...) {
	char buf[1024];
	va_list va;
	va_start(va, msg);
	vsprintf(buf, msg, va);
	va_end(va);
#ifdef __ANDROID__
	__android_log_print(ANDROID_LOG_ERROR, "BSEngine", "ERROR: %s!", buf);
#endif
	fprintf(stderr, "ERROR: %s!\n", buf);
	fflush(stderr);
	exit(-1);
}

void warning(const char *msg, ...) {
	char buf[1024];
	va_list va;
	va_start(va, msg);
	vsprintf(buf, msg, va);
	va_end(va);
#ifdef __ANDROID__
	__android_log_print(ANDROID_LOG_WARN, "BSEngine", "WARNING: %s!", buf);
#endif
	fprintf(stderr, "WARNING: %s!\n", buf);
	fflush(stderr);
}
