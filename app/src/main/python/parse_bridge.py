"""
ParseHub Android App - Python 解析桥接层
通过 Chaquopy 在安卓 App 中嵌入 Python 解析引擎
"""

import asyncio
import json
import os
import sys
from pathlib import Path


def _get_android_files_dir():
    """获取 Android files 目录路径"""
    try:
        from com.chaquo.python import Python
        context = Python.getPlatform().getApplication()
        return str(context.getFilesDir().getAbsolutePath())
    except Exception:
        return str(Path.home() / ".parsehub")


def _ensure_data_dir():
    """确保数据目录存在"""
    data_dir = Path(_get_android_files_dir()) / "data"
    data_dir.mkdir(parents=True, exist_ok=True)
    (data_dir / "downloads").mkdir(exist_ok=True)
    (data_dir / "cache").mkdir(exist_ok=True)
    return data_dir


_data_dir = _ensure_data_dir()
_download_dir = _data_dir / "downloads"

_parse_service = None
_loop = None


def _get_loop():
    """获取或创建事件循环"""
    global _loop
    if _loop is None:
        try:
            _loop = asyncio.get_event_loop()
        except RuntimeError:
            _loop = asyncio.new_event_loop()
            asyncio.set_event_loop(_loop)
    return _loop


def _run_async(coro):
    """同步运行异步函数（用于 Java 调用）"""
    loop = _get_loop()
    if loop.is_running():
        future = asyncio.run_coroutine_threadsafe(coro, loop)
        return future.result(timeout=120)
    else:
        return loop.run_until_complete(coro)


def get_service():
    """获取 ParseService 单例"""
    global _parse_service
    if _parse_service is None:
        from parsehub import ParseHub
        _parse_service = ParseHub()
    return _parse_service


def get_supported_platforms():
    """获取支持的平台列表（JSON 字符串）"""
    try:
        service = get_service()
        platforms = service.get_platforms()
        return json.dumps(platforms, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)


def get_platform(url: str) -> str:
    """检测 URL 对应的平台（JSON 字符串）"""
    try:
        service = get_service()
        platform = service.get_platform(url)
        if platform:
            return json.dumps({
                "id": platform.id,
                "name": platform.name,
            }, ensure_ascii=False)
        return json.dumps({"error": "不支持的平台"}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)


def parse(url: str, cookie: str = None, proxy: str = None) -> str:
    """
    解析 URL（JSON 字符串）
    
    Args:
        url: 要解析的链接
        cookie: 可选，平台 Cookie
        proxy: 可选，代理地址
    
    Returns:
        JSON 格式的解析结果
    """
    async def _parse():
        service = get_service()
        max_retries = 3
        last_error = None
        
        for attempt in range(1, max_retries + 1):
            try:
                result = await service.parse(
                    url,
                    cookie=cookie,
                    proxy=proxy
                )
                return _result_to_dict(result)
            except Exception as e:
                last_error = e
                if attempt >= max_retries:
                    raise
        
        if last_error:
            raise last_error
        return None
    
    try:
        result = _run_async(_parse())
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)


def get_raw_url(url: str, proxy: str = None, clean_all: bool = True) -> str:
    """获取原始/清理后的 URL（JSON 字符串）"""
    async def _get_raw_url():
        service = get_service()
        max_retries = 3
        last_error = None
        
        for attempt in range(1, max_retries + 1):
            try:
                raw_url = await service.get_raw_url(
                    url,
                    proxy=proxy,
                    clean_all=clean_all
                )
                return {"raw_url": str(raw_url)}
            except Exception as e:
                last_error = e
                if attempt >= max_retries:
                    raise
        
        if last_error:
            raise last_error
        return None
    
    try:
        result = _run_async(_get_raw_url())
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)


def download(url: str, cookie: str = None, proxy: str = None, 
             save_metadata: bool = False) -> str:
    """
    下载媒体文件（JSON 字符串）
    
    Returns:
        JSON 格式的下载结果，包含文件路径列表
    """
    async def _download():
        service = get_service()
        parse_result = await service.parse(url, cookie=cookie, proxy=proxy)
        
        output_dir = Path(_download_dir) / f"dl_{abs(hash(url)) % 1000000}"
        output_dir.mkdir(parents=True, exist_ok=True)
        
        download_result = await parse_result.download(
            output_dir,
            proxy=proxy,
            save_metadata=save_metadata
        )
        
        return {
            "output_dir": str(download_result.output_dir),
            "media": [
                {
                    "type": type(m).__name__,
                    "path": str(m.path) if hasattr(m, 'path') else None,
                    "url": str(m.url) if hasattr(m, 'url') else None,
                    "width": getattr(m, 'width', None),
                    "height": getattr(m, 'height', None),
                    "duration": getattr(m, 'duration', None),
                    "size": getattr(m, 'size', None),
                    "thumb_url": str(m.thumb_url) if hasattr(m, 'thumb_url') and m.thumb_url else None,
                }
                for m in (download_result.media 
                         if isinstance(download_result.media, list) 
                         else [download_result.media])
            ]
        }
    
    try:
        result = _run_async(_download())
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)


def _result_to_dict(result) -> dict:
    """将解析结果转换为字典"""
    media_list = []
    media = result.media if hasattr(result, 'media') else None
    
    if media:
        if not isinstance(media, list):
            media = [media]
        for m in media:
            media_dict = {
                "type": type(m).__name__,
                "url": str(m.url) if hasattr(m, 'url') and m.url else None,
                "thumb_url": str(m.thumb_url) if hasattr(m, 'thumb_url') and m.thumb_url else None,
                "width": getattr(m, 'width', None),
                "height": getattr(m, 'height', None),
                "duration": getattr(m, 'duration', None),
                "size": getattr(m, 'size', None),
                "ext": getattr(m, 'ext', None),
            }
            media_list.append(media_dict)
    
    return {
        "platform": str(result.platform) if hasattr(result, 'platform') else None,
        "type": str(result.type) if hasattr(result, 'type') else None,
        "title": result.title if hasattr(result, 'title') else "",
        "content": result.content if hasattr(result, 'content') else "",
        "raw_url": str(result.raw_url) if hasattr(result, 'raw_url') and result.raw_url else None,
        "author": result.author if hasattr(result, 'author') else None,
        "avatar": str(result.avatar) if hasattr(result, 'avatar') and result.avatar else None,
        "media": media_list,
        "markdown_content": result.markdown_content if hasattr(result, 'markdown_content') else None,
    }


def get_download_dir() -> str:
    """获取下载目录路径"""
    return str(_download_dir)


def clear_downloads() -> str:
    """清理下载目录"""
    try:
        import shutil
        if _download_dir.exists():
            shutil.rmtree(_download_dir)
            _download_dir.mkdir()
        return json.dumps({"success": True}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)
