import requests
import json
import time
from datetime import datetime, timedelta
import pymysql
import psycopg2
import sys

# dev/prod
MODE = 'prod'

seatunnel_host = "168.188.5.88"
seatunnel_port = 5801
seatunnel_base_url = f"http://{seatunnel_host}:{seatunnel_port}/hazelcast/rest/maps"

def submit_job(payload):
    try:
        headers = {"Content-Type": "application/json;charset=utf-8"}
        url = f"{seatunnel_base_url}/submit-job"
        
        response = requests.post(url=url, headers=headers, data=payload)
        result = response.json()
        output = json.dumps(result, indent=4)
        print(output)
        if "jobId" in output:
            print("submit success!")
            return result["jobId"]
        else:
            print("submit failed!")
            exit(1)
    except Exception as e:
        print(f"error: {e}")
        exit(1)

def monitor_job(job_id):
    """任务监控"""
    start_time = datetime.now()
    last_source_records = 0
    last_sink_records = 0
    
    print(f"\n{'时间':<8} | {'状态':<12} | {'源记录数':>11} | {'源速率(rec/s)':>14} | {'写记录数':>11} | {'写速率(rec/s)':>14} | {'耗时(s)':>8}")
    print("-" * 125)
    
    while True:
        try:
            status_url = f"{seatunnel_base_url}/job-info/{job_id}"
            resp = requests.get(status_url, timeout=10)
            if resp.status_code != 200:
                print(f"状态查询失败: {resp.text}")
                time.sleep(10)
                continue
                
            data = resp.json()
            current_status = data.get("jobStatus", "UNKNOWN")
            metrics = data.get("metrics", {})
            
            # 获取 Source 和 Sink 的记录数
            current_source_records = int(metrics.get("SourceReceivedCount", "0"))
            current_sink_records = int(metrics.get("SinkWriteCount", "0"))

            elapsed = (datetime.now() - start_time).total_seconds()
            source_rate = (current_source_records - last_source_records) / 10
            sink_rate = (current_sink_records - last_sink_records) / 10
        
            # 打印信息
            print(
                f"{datetime.now().strftime('%H:%M:%S'):<8} | "
                f"{current_status:<12} | "
                f"{current_source_records:>11,} | "
                f"{source_rate:>14,.1f} | "
                f"{current_sink_records:>11,} | "
                f"{sink_rate:>14,.1f} | "
                f"{elapsed:>8.1f}"
            )
            
            # 终止条件
            if current_status == "FINISHED":
                avg_source_rate = current_source_records / elapsed if elapsed > 0 else 0
                avg_sink_rate = current_sink_records / elapsed if elapsed > 0 else 0
                
                print(f"\n任务成功完成")
                print(f"记录数: 源 {current_source_records:,} | 写 {current_sink_records:,}")
                print(f"平均速率: 源 {avg_source_rate:,.1f} rec/s | 写 {avg_sink_rate:,.1f} rec/s")
                break
            elif current_status in ["FAILED", "CANCELED"]:
                print(f"\n任务异常终止: {current_status}")
                if "errorMsg" in data:
                    print(f"错误详情: {data['errorMsg']}")
                break
                
            last_source_records = current_source_records
            last_sink_records = current_sink_records
            time.sleep(10)
            
        except Exception as e:
            print(f"监控异常: {type(e).__name__} - {str(e)}")
            time.sleep(10)

if __name__ == "__main__":
    bizDate = '20260419'
    source_host = '168.188.44.220'
    source_port = 9030
    source_database = 'tmsdb'
    source_user = 'tms'
    source_password = 'TMS@tms1010'
    target_ip = '192.168.119.226'
    target_port = 4000
    target_database = 'tmsnser'
    target_user = 'tmsbasic'
    target_pwd = 'zdG1zYmFzaWNo'
    stable_name = 'a_fin_caln'
    ttable_name = 'bs_etl_financial_calendar'
    sync_cols = 'DATA_DT,GROUP_CUST_NO,CORP_NO,CORP_NAME,PROD_CD,PROD_TYPE,PROD_STATUS,CURY_CD,CURY_CN,ORGNL_PROD_AMT,ORGNL_PROD_INT,PROD_DT,DATA_MODULE,DATA_SRC'
    sync_condition = "DATA_DT = CAST('20260419' AS DATE)"
    
    config = {
        "env": {
            "parallelism": 8,
            "job.retry.times": 0,
            "job.mode": "BATCH",
            "job.name": "DataOut_to_tmsnser_a_fin_caln"
        },
        "source": [
            {        
                "plugin_name": "Doris",
                "fenodes": f"168.188.44.31:8030",
                "username": source_user,
                "password": source_password,
                "database": source_database,
                "table": stable_name,
                "doris.read.field": sync_cols,
                "doris.filter.query": sync_condition,
                "doris.batch.size": 50000,
                "result_table_name": "table1",
                "doris.request.tablet.size": 8
            }
        ],
        "sink": [
            {
                "source_table_name": "table1",
                "plugin_name": "Jdbc",
                "url": f"jdbc:mysql://{target_ip}:{target_port}/{target_database}",
                "driver": "com.mysql.cj.jdbc.Driver",
                "user": target_user,
                "password": target_pwd,
                "generate_sink_sql": "true",
                "database": target_database,
                "table": f"tmsnser.{ttable_name}",
                "field_ide": "LOWERCASE",
                "schema_save_mode": "IGNORE",
                "enable_upsert": "false",
                "batch_size": 10000,
                "properties": {
                    "rewriteBatchedStatements": "true",
                    "useServerPrepStmts": "false"
                }   
            }
        ]
    }

    print(json.dumps(config, indent=4))
    job_id = submit_job(json.dumps(config))
    print(f"job_id:{job_id}")
    monitor_job(job_id)
