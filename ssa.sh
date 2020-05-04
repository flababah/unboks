#!/bin/sh
clang -S -emit-llvm -Xclang -disable-O0-optnone "$1" -o temp.ll
opt -mem2reg temp.ll -o temp.bc
llvm-dis temp.bc -o "$1".ll
rm temp.ll
rm temp.bc
