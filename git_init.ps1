# git_init.ps1
# 在 WeChatRedPacket 目录下运行本脚本，自动初始化仓库并推送到 GitHub（触发云编译）。
# 前置：
#   1. 已安装 Git（https://git-scm.com/download/win）
#   2. 已在 GitHub 新建一个空仓库，拿到仓库 URL
#   3. GitHub 已配置凭证（SSH key，或 Git 凭据管理器 / Personal Access Token）
# 用法：
#   .\git_init.ps1 -RepoUrl https://github.com/你的用户名/仓库名.git

param(
    [Parameter(Mandatory = $true)]
    [string]$RepoUrl
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Error "未检测到 git，请先安装 Git for Windows：https://git-scm.com/download/win"
    exit 1
}

git init
git add .
git commit -m "init: 微信红包助手（Android 无障碍 + R8 混淆 + GitHub 云编译）"
git branch -M main
git remote remove origin 2>$null
git remote add origin $RepoUrl
git push -u origin main

Write-Output ""
Write-Output "已推送到 $RepoUrl"
Write-Output "接下来：GitHub 仓库 -> Actions -> Build Debug APK -> Run workflow -> 下载产物 app-debug.apk"
