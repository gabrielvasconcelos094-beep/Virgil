cmake_minimum_required(VERSION 3.22)
project(virgil_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/llama.cpp)

# Apenas a biblioteca: sem ferramentas, exemplos, servidor, testes ou rede.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

if(ANDROID)
    # Variantes de CPU carregadas em tempo de execução: o mesmo APK usa dotprod/i8mm/SVE quando
    # o processador tem, e ainda funciona em núcleos antigos (sem travar por instrução ilegal).
    set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
else()
    # Build de host (Linux/macOS) usado nos testes de JVM com o modelo real.
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
    find_package(JNI REQUIRED)
endif()

add_subdirectory(${LLAMA_SRC} llama-build)

add_library(virgil_llama SHARED virgil_llama.cpp)
target_include_directories(virgil_llama PRIVATE ${LLAMA_SRC}/include ${LLAMA_SRC}/ggml/include)
target_link_libraries(virgil_llama PRIVATE llama ggml)

if(ANDROID)
    target_link_libraries(virgil_llama PRIVATE log)
    # Páginas de 16 KB (exigência do Google Play para Android 15+).
    target_link_options(virgil_llama PRIVATE "-Wl,-z,max-page-size=16384")
else()
    target_include_directories(virgil_llama PRIVATE ${JNI_INCLUDE_DIRS})
endif()
