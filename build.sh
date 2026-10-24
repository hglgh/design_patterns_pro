#!/usr/bin/env bash
# ============================================================
# Design Patterns Pro - Linux/macOS 一键编译运行脚本
# 需要 JDK 25
# ============================================================
set -e

JDK_HOME="${JDK_HOME:-/usr/lib/jvm/jdk-25}"
JAVAC="$JDK_HOME/bin/javac"
JAVA="$JDK_HOME/bin/java"
SRC_DIR="src"
OUT_DIR="out"

if [ ! -x "$JAVAC" ]; then
    # 尝试使用系统 PATH 中的 javac
    if command -v javac >/dev/null 2>&1; then
        JAVAC="javac"
        JAVA="java"
    else
        echo "[ERROR] 未找到 JDK，请设置 JDK_HOME 环境变量"
        exit 1
    fi
fi

echo "============================================================"
echo "[1/3] 清理输出目录 $OUT_DIR"
echo "============================================================"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

echo "============================================================"
echo "[2/3] 编译全部源码（--release 25）"
echo "============================================================"
find "$SRC_DIR" -name "*.java" > sources.txt
"$JAVAC" --release 25 -encoding UTF-8 -d "$OUT_DIR" "@sources.txt"
rm sources.txt
echo "[OK] 编译成功"

echo "============================================================"
echo "[3/3] 依次运行每个模式的 Client"
echo "============================================================"

# 查找所有 *Client.class（SingletonClient 与组合示例的 Client 都包含在内）
MAIN_CLASSES=$(find "$OUT_DIR" -name "*Client.class" | \
    sed "s|$OUT_DIR/||; s|\.class$||; s|/|.|g")

for CLS in $MAIN_CLASSES; do
    echo ""
    echo "------------------------------------------------------------"
    echo "运行：$CLS"
    echo "------------------------------------------------------------"
    "$JAVA" -cp "$OUT_DIR" "$CLS" || echo "[WARN] $CLS 运行失败"
done

echo ""
echo "============================================================"
echo "全部运行完毕"
echo "============================================================"
