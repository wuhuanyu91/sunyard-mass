-- Docker 环境 PostgreSQL 初始化脚本
-- 由 pgvector/pgvector 镜像的 POSTGRES_INITDB_ARGS 触发执行

-- 创建 mas 用户与数据库
CREATE USER mas WITH PASSWORD 'mas123';
CREATE DATABASE mas OWNER mas;

-- 在 mas 数据库中启用 pgvector 扩展
\c mas
CREATE EXTENSION IF NOT EXISTS vector;
