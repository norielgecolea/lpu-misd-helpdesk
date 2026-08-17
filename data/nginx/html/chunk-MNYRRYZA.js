var __defProp = Object.defineProperty;
var __defProps = Object.defineProperties;
var __getOwnPropDescs = Object.getOwnPropertyDescriptors;
var __getOwnPropSymbols = Object.getOwnPropertySymbols;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __propIsEnum = Object.prototype.propertyIsEnumerable;
var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
var __spreadValues = (a, b) => {
  for (var prop in b ||= {})
    if (__hasOwnProp.call(b, prop))
      __defNormalProp(a, prop, b[prop]);
  if (__getOwnPropSymbols)
    for (var prop of __getOwnPropSymbols(b)) {
      if (__propIsEnum.call(b, prop))
        __defNormalProp(a, prop, b[prop]);
    }
  return a;
};
var __spreadProps = (a, b) => __defProps(a, __getOwnPropDescs(b));
var __restKey = (key) => typeof key === "symbol" ? key : key + "";
var __objRest = (source, exclude) => {
  var target = {};
  for (var prop in source)
    if (__hasOwnProp.call(source, prop) && exclude.indexOf(prop) < 0)
      target[prop] = source[prop];
  if (source != null && __getOwnPropSymbols)
    for (var prop of __getOwnPropSymbols(source)) {
      if (exclude.indexOf(prop) < 0 && __propIsEnum.call(source, prop))
        target[prop] = source[prop];
    }
  return target;
};
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};

// node_modules/@azure/msal-browser/dist/telemetry/BrowserPerformanceMeasurement.mjs
/*! @azure/msal-browser v5.18.0 2026-08-04 */
var BrowserPerformanceMeasurement = class _BrowserPerformanceMeasurement {
  constructor(name, correlationId) {
    this.correlationId = correlationId;
    this.measureName = _BrowserPerformanceMeasurement.makeMeasureName(name, correlationId);
    this.startMark = _BrowserPerformanceMeasurement.makeStartMark(name, correlationId);
    this.endMark = _BrowserPerformanceMeasurement.makeEndMark(name, correlationId);
  }
  static makeMeasureName(name, correlationId) {
    return `msal.measure.${name}.${correlationId}`;
  }
  static makeStartMark(name, correlationId) {
    return `msal.start.${name}.${correlationId}`;
  }
  static makeEndMark(name, correlationId) {
    return `msal.end.${name}.${correlationId}`;
  }
  static supportsBrowserPerformance() {
    return typeof window !== "undefined" && typeof window.performance !== "undefined" && typeof window.performance.mark === "function" && typeof window.performance.measure === "function" && typeof window.performance.clearMarks === "function" && typeof window.performance.clearMeasures === "function" && typeof window.performance.getEntriesByName === "function";
  }
  /**
   * Flush browser marks and measurements.
   * @param {string} correlationId
   * @param {SubMeasurement} measurements
   */
  static flushMeasurements(correlationId, measurements) {
    if (_BrowserPerformanceMeasurement.supportsBrowserPerformance()) {
      try {
        measurements.forEach((measurement) => {
          const measureName = _BrowserPerformanceMeasurement.makeMeasureName(measurement.name, correlationId);
          const entriesForMeasurement = window.performance.getEntriesByName(measureName, "measure");
          if (entriesForMeasurement.length > 0) {
            window.performance.clearMeasures(measureName);
            window.performance.clearMarks(_BrowserPerformanceMeasurement.makeStartMark(measureName, correlationId));
            window.performance.clearMarks(_BrowserPerformanceMeasurement.makeEndMark(measureName, correlationId));
          }
        });
      } catch (e) {
      }
    }
  }
  startMeasurement() {
    if (_BrowserPerformanceMeasurement.supportsBrowserPerformance()) {
      try {
        window.performance.mark(this.startMark);
      } catch (e) {
      }
    }
  }
  endMeasurement() {
    if (_BrowserPerformanceMeasurement.supportsBrowserPerformance()) {
      try {
        window.performance.mark(this.endMark);
        window.performance.measure(this.measureName, this.startMark, this.endMark);
      } catch (e) {
      }
    }
  }
  flushMeasurement() {
    if (_BrowserPerformanceMeasurement.supportsBrowserPerformance()) {
      try {
        const entriesForMeasurement = window.performance.getEntriesByName(this.measureName, "measure");
        if (entriesForMeasurement.length > 0) {
          const durationMs = entriesForMeasurement[0].duration;
          window.performance.clearMeasures(this.measureName);
          window.performance.clearMarks(this.startMark);
          window.performance.clearMarks(this.endMark);
          return durationMs;
        }
      } catch (e) {
      }
    }
    return null;
  }
};

export {
  __spreadValues,
  __spreadProps,
  __restKey,
  __objRest,
  __export,
  BrowserPerformanceMeasurement
};
//# debugId=aaa6e34a-a335-5f9c-abd3-203ced8085f2
//# sourceMappingURL=chunk-MNYRRYZA.js.map
