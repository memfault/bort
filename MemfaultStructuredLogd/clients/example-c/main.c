#include <stdio.h>
#include <structuredlog.h>

int main(int argc, char *argv[]) {

  if (argc != 3) {
    fprintf(stderr, "usage: %s <tag> <message>", argv[0]);
    return -1;
  }

  structured_logger_t logger = structured_log_new();
  structured_log(logger, argv[1], argv[2]);
  structured_log_destroy(logger);

  return 0;
}
