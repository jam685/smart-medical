# Git 常用命令速查表

> 适用仓库：`D:\Mygit`（智能医疗辅助系统源码归档仓库，分支 master）
> 执行任何命令前，请先确保终端在仓库目录下：`cd D:\Mygit`

---

## 一、查看与了解

| 目的 | 命令 |
|---|---|
| 查看所有版本编号 | `git log --oneline` |
| 查看版本（含日期/作者） | `git log --pretty=format:"%h  %ad  %an  \|  %s" --date=format:'%Y-%m-%d %H:%M'` |
| 查看当前改动状态 | `git status` |
| 查看所有配置了远程仓库 | `git remote -v` |
| 查看当前分支 | `git branch` 或 `git branch -a` |

**示例输出（git log --oneline）：**
```
7571395 初始版本：智能医疗辅助系统（小林智疗）前后端源码归档
```
> 每行开头的 7 位字符（如 `7571395`）就是这个版本的**版本号**，是它的唯一 ID。

---

## 二、保存新版本（核心三步）

```powershell
cd D:\Mygit

git add .                          # 第1步：把改动文件放进“暂存区”
git commit -m "这次改了什么，写清楚"  # 第2步：正式存成一个带说明的版本
git status                          # 第3步：确认（看到 nothing to commit 即成功）
```

**形象理解：**
- `git add .` = 挑选要保存的文件
- `git commit -m "说明"` = 封箱、贴上“版本标签”
- 每 commit 一次，就多一个可回退的版本

---

## 三、取看某版本的内容

| 想做什么 | 命令 |
|---|---|
| 看某版本改了哪些文件 | `git show 版本号 --stat` |
| 看某版本里某个文件的内容 | `git show 版本号:文件路径` |
| 还原工作区到某一版（慎用） | `git checkout 版本号` |

**示例：**
```powershell
git show 7571395 --stat                             # 这个版本改了哪些文件
git show 7571395:smart-medical-frontend/src/App.vue # 这个版本里 App.vue 的内容
```

> `git show` 只看不改，安全；
> `git checkout 版本号` 会改动工作区文件，动手前先确认。

---

## 四、查看具体改动

| 目的 | 命令 |
|---|---|
| 看某文件当前未提交的改动 | `git diff 文件路径` |
| 看所有未提交改动 | `git diff` |
| 看已暂存（已 add 未 commit）的改动 | `git diff --cached` |
| 看某两次版本之间的差异 | `git diff 版本A 版本B` |

---

## 五、⚠️ 高风险命令（易丢失/覆盖，慎用，使用前先咨询）

| 命令 | 风险 |
|---|---|
| `git reset --hard` | 丢弃所有未提交改动，不可恢复 |
| `git checkout .` / `git restore .` | 还原未提交修改，丢弃改动 |
| `git reset --hard 版本号` | 硬回退到某版本，之后的提交会丢 |
| `git push -f` | 强制推送，会覆盖远端历史 |
| `git clean -fd` | 删除所有未跟踪文件 |

**原则：** 这五类命令会丢东西，动手前**先问我**。

---

## 六、后台运行到多版本后的常用操作

```powershell
git log --oneline                     # 版本清单（相当于归档目录）
# 想让某版本恢复出来看：
git show 版本号:文件路径                # 单个文件看内容
```

---

## 七、本仓库当前约定（与我约定）

- 主分支：`master`
- 归档方式：源码从 `D:\smart-medical` 拷入 `D:\Mygit` 后 commit
- 已排除入档：`node_modules / target / uploads` 等运行产物（见 `.gitignore`）

---

### 冷知识
- git 不是“一个版本一个文件夹”，而是把所有历史快照压缩存放于 `.git\objects\`，靠命令取用。
- 每 commit 一次，旧版本都会永久保留，**不会覆盖**，随时可回退。