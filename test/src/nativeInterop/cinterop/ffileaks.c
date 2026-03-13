#include "ffileaks.h"

#include <stdlib.h>

void* leakt_native_alloc(size_t size) {
    return malloc(size);
}

void leakt_native_free(void* ptr) {
    free(ptr);
}
