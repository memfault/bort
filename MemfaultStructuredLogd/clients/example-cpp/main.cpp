#include <cstdio>
#include <memory>
#include <string>

#include <structuredlog.h>

using namespace memfault;

int main(int argc, char *argv[]) {

  if (argc != 3) {
    fprintf(stderr, "usage: %s <tag> <message>", argv[0]);
    return -1;
  }

  auto logger = std::make_unique<StructuredLogger>();
  logger->log(std::string(argv[1]), std::string(argv[2]));

  return 0;
}
