<template>
  <error-state code="500" title="服务暂时没有响应"
    hint="稍等片刻重试一次。如果反复出现，请把发生的时间和你的操作步骤告诉管理员——后台按 500ms 阈值记录了慢接口与异常请求，能定位到具体那一次。" />
</template>

<script setup>
import ErrorState from '@/components/ErrorState'
</script>
