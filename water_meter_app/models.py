from dataclasses import dataclass
from typing import Dict, Any

@dataclass
class WaterRecord:
    cusName: str = ""
    usersNo: str = ""
    waterMeterNo: str = ""
    telNum: str = ""
    cusAdd: str = ""
    areaName: str = ""
    balance: float = 0.0
    meterBalance: float = 0.0
    receiveTime: str = ""
    valveState: str = ""
    totalPositiveWater: float = 0.0
    imei: str = ""
    signalStrength: str = ""
    batteryVoltage: str = ""

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "WaterRecord":
        return cls(
            cusName=data.get("cusName", ""),
            usersNo=data.get("usersNo", ""),
            waterMeterNo=data.get("waterMeterNo", ""),
            telNum=data.get("telNum", ""),
            cusAdd=data.get("cusAdd", ""),
            areaName=data.get("areaName", ""),
            balance=float(data.get("balance", 0.0)),
            meterBalance=float(data.get("meterBalance", 0.0)),
            receiveTime=data.get("receiveTime", ""),
            valveState=data.get("valveState", ""),
            totalPositiveWater=float(data.get("totalPositiveWater", 0.0)),
            imei=data.get("imei", ""),
            signalStrength=data.get("signalStrength", ""),
            batteryVoltage=data.get("batteryVoltage", ""),
        )


@dataclass
class CustomerDetail:
    cusNo: str = ""
    cusName: str = ""
    waterMeterNo: str = ""
    telNum: str = ""
    cusAdd: str = ""
    havePactName: str = ""
    cusWaterNatureName: str = ""
    cusStateName: str = ""
    balance: float = 0.0
    waterMeterTypeName: str = ""
    meterDiameter: str = ""
    waterMeterRemark: str = ""
    waterAdd: str = ""
    areaName: str = ""
    meterReadName: str = ""
    meterReadBookName: str = ""
    meterDate: str = ""
    monthNum: float = 0.0
    totalNum: float = 0.0
    lastRead: float = 0.0
    thisRead: float = 0.0
    valveStatusName: str = ""

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "CustomerDetail":
        return cls(
            cusNo=data.get("cusNo", ""),
            cusName=data.get("cusName", ""),
            waterMeterNo=data.get("waterMeterNo", ""),
            telNum=data.get("telNum", ""),
            cusAdd=data.get("cusAdd", ""),
            havePactName=data.get("havePactName", ""),
            cusWaterNatureName=data.get("cusWaterNatureName", ""),
            cusStateName=data.get("cusStateName", ""),
            balance=float(data.get("balance", 0.0)),
            waterMeterTypeName=data.get("waterMeterTypeName", ""),
            meterDiameter=data.get("meterDiameter", ""),
            waterMeterRemark=data.get("waterMeterRemark", ""),
            waterAdd=data.get("waterAdd", ""),
            areaName=data.get("areaName", ""),
            meterReadName=data.get("meterReadName", ""),
            meterReadBookName=data.get("meterReadBookName", ""),
            meterDate=data.get("meterDate", ""),
            monthNum=float(data.get("monthNum", 0.0)),
            totalNum=float(data.get("totalNum", 0.0)),
            lastRead=float(data.get("lastRead", 0.0)),
            thisRead=float(data.get("thisRead", 0.0)),
            valveStatusName=data.get("valveStatusName", ""),
        )